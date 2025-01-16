/*
 * This file is part of bean-runner.
 *
 * Copyright (C) 2025 Dan Bar-Yaakov
 *
 * bean-runner is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * bean-runner is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 */

package org.beanrunner.core.views;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.ListDataProvider;
import lombok.Getter;
import org.beanrunner.core.*;
import org.beanrunner.core.annotations.*;
import org.vaadin.addons.visjs.network.main.Edge;
import org.vaadin.addons.visjs.network.main.NetworkDiagram;
import org.vaadin.addons.visjs.network.main.Node;
import org.vaadin.addons.visjs.network.options.Options;
import org.vaadin.addons.visjs.network.options.edges.Edges;
import org.vaadin.addons.visjs.network.options.nodes.NodeColor;
import org.vaadin.addons.visjs.network.options.nodes.Nodes;
import org.vaadin.addons.visjs.network.util.Shadow;
import org.vaadin.addons.visjs.network.util.Shape;
import org.vaadin.addons.visjs.network.util.SimpleColor;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Consumer;

public class DiagramView extends VerticalLayout {

    private final MainView mainView;

    private NetworkDiagram nd;
    private final List<Node> nodes = new ArrayList<>();
    private final ListDataProvider<Node> nodeDataProvider = new ListDataProvider<>(nodes);
    private final List<Edge> edges = new ArrayList<>();
    private final ListDataProvider<Edge> edgeDataProvider = new ListDataProvider<>(edges);
    private String selectedNodeId;
    private Step<?> selectedRootTask;
    private FlowRunIdentifier selectedIdentifier;
    @Getter
    private Step<?> selectedStep;
    private Map<Integer, StepStatus> clusters = new HashMap<>();

    @Getter
    private boolean expandedState = true;

    public DiagramView(MainView mainView) {
        this.mainView = mainView;

        setPadding(false);
        setSpacing(false);
        setMargin(false);
        setSizeFull();
        SimpleColor highlightColor = new SimpleColor();
        highlightColor.setBorderColor("black");
        nd = new NetworkDiagram(Options.builder()
                        .withAutoResize(true)
                        .withEdges(Edges.builder().withShadow(Shadow.builder().withSize(40).withColor("black").build()).build())
                        .withNodes(Nodes.builder().withSize(20).withBorderWidth(2).withShadow(Shadow.builder().withEnabled(true).withColor("darkgray").build()).withColor(NodeColor.builder().withHighlightColor(highlightColor).build())
                                .build()).build());
        nd.setSizeFull();
        nd.setClassName("diagram");

        nd.addDeselectNodeListener(ls -> {
            selectedNodeId = null;
            mainView.diagramTaskSelected(null);
        });

        nd.addSelectNodeListener(ls -> {
            String nodeId = ls.getParams().getArray("nodes").getString(0);
            selectedNodeId = nodeId;
            selectedStep = null;
            for (Step<?> task : mainView.getStepManager().getAllSteps()) {
                if (getTaskIdentifier(task).equals(nodeId)) {
                    selectedStep = task;
                    mainView.diagramTaskSelected(task);
                    break;
                }
            }

        });

        nd.setNodesDataProvider(nodeDataProvider);
        nd.setEdgesDataProvider(edgeDataProvider);
        add(nd);
    }

    public void setSelectedFlow(Step<?> task, FlowRunIdentifier identifier) {
        boolean taskChanged = selectedRootTask != task;
        selectedRootTask = task;
        selectedIdentifier = identifier;

        if (taskChanged) {
            clusters.clear();
            populateGraphDiagram();
            updateGraphDiagram();
        } else {
            updateGraphDiagram();
        }
        if (mainView.getSelectedFlowSteps() != null) {
            for (Step<?> step : mainView.getSelectedFlowSteps()) {
                if (!clusters.isEmpty() && step.getClusterId() > 0) {
                    updateClusterColorFor(step);
                }
            }
        }
    }

    public void populateGraphDiagram() {
        nodes.clear();
        edges.clear();

        populateGraphDiagram(selectedRootTask, new HashSet<>());


        for (Node node : nodes) {
            NodePosition position = mainView.getPositionsService().getPosition(node.getId());
            if (position != null) {
                node.setX(position.x());
                node.setY(position.y());
                node.setPhysics(false);
            }
        }

        nodeDataProvider.refreshAll();
        nd.diagramFit();

    }

    public void updateGraphDiagram() {
        populateGraphDiagram(selectedRootTask, new HashSet<>());
    }

    private String colorForTask(Step<?> task, FlowRunIdentifier identifier) {
        if (identifier == null) {
            return "white";
        }
        StepStatus status = task.getStatus(identifier);
        return colorForStatus(status);
    }

    private String colorForStatus(StepStatus status) {
        if (status == StepStatus.RUNNING) {
            return "yellow";
        } else if (status == StepStatus.SUCCESS) {
            return "#99ff6e";
        } else if (status == StepStatus.FAILED) {
            return "red";
        } else if (status == StepStatus.FAILED_TRANSITIVELY) {
            return "lightgray";
        } else if (status == StepStatus.PENDING_REWIND || status == StepStatus.REWINDING) {
            return "orange";
        } else if (status == StepStatus.REWIND_SUCCESS) {
            return "#26c776";
        } else if (status == StepStatus.REWIND_FAILED) {
            return "darkred";
        }
        return "white";
    }

    private String borderForTask(Step<?> task, FlowRunIdentifier identifier) {
        if (identifier == null) {
            return "darkgray";
        }
        StepStatus status = task.getStatus(identifier);
        return borderForStatus(status);
    }

    private static String borderForStatus(StepStatus status) {
        if (status == StepStatus.RUNNING) {
            return "#a69924";
        } else if (status == StepStatus.SUCCESS) {
            return "#1d8236";
        } else if (status == StepStatus.FAILED) {
            return "#8a1e2e";
        } else if (status == StepStatus.FAILED_TRANSITIVELY) {
            return "#a3a3a3";
        } else if (status == StepStatus.PENDING_REWIND || status == StepStatus.REWINDING) {
            return "#996100";
        } else if (status == StepStatus.REWIND_SUCCESS) {
            return "#1d751d";
        } else if (status == StepStatus.REWIND_FAILED) {
            return "#4d0218";
        }
        return "darkgray";
    }

    private void populateGraphDiagram(Step<?> task, Set<Step<?>> visitedTasks) {
        if (visitedTasks.contains(task)) {
            return;
        }
        visitedTasks.add(task);
        if (nodes.stream().noneMatch(n -> n.getId().equals(getTaskIdentifier(task)))) {
            Node node = new Node();
             boolean isRoot = visitedTasks.size() == 1;
            applyNodeAttributes(task, node, isRoot);
            nodes.add(node);
        } else {

            Node node = nodes.stream().filter(n -> n.getId().equals(getTaskIdentifier(task))).findFirst().orElse(null);
            if (node != null) {
                applyNodeAttributes(task, node, visitedTasks.size() == 1);
                nd.updateNode(node);

            }

        }

        List<Step<?>> dependentTasks = mainView.getStepManager().getChildSteps(task);
        for (Step<?> dependentTask : dependentTasks) {
            if (isHidden(dependentTask)) {
                continue;
            }
            if (nodes.stream().noneMatch(n -> n.getId().equals(getTaskIdentifier(dependentTask)))) {
                Node dependentNode = new Node();
                applyNodeAttributes(dependentTask, dependentNode, false);
                nodes.add(dependentNode);
            } else {
                Node dependentNode = nodes.stream().filter(n -> n.getId().equals(getTaskIdentifier(dependentTask))).findFirst().orElse(null);
                if (dependentNode != null) {
                    applyNodeAttributes(dependentTask, dependentNode, false);
                    nd.updateNode(dependentNode);

                }
            }

            if (edges.stream().noneMatch(e -> e.getFrom().equals(getTaskIdentifier(task)) && e.getTo().equals(getTaskIdentifier(dependentTask)))) {
                Edge edge = new Edge(getTaskIdentifier(task), getTaskIdentifier(dependentTask));
                for (Field field : ReflectionUtils.getFields(dependentTask.getClass())) {
                    try {
                        field.setAccessible(true);
                        if (field.get(dependentTask) == task) {
                            if (field.isAnnotationPresent(OnSuccess.class)) {
                                edge.setColor("#71bf97");
                            } else if (field.isAnnotationPresent(OnUpstreamFailure.class)) {
                                edge.setColor("#ff7d95");
                            } else if (field.isAnnotationPresent(OnComplete.class)) {
                                edge.setColor("#64b2d1");
                            }
                        }
                    } catch (IllegalAccessException e) {
                        // ignore
                    }
                }
                edge.setWidth(3);
                edge.setArrows("to");
                edges.add(edge);
            }

            populateGraphDiagram(dependentTask, visitedTasks);
        }
    }
    private boolean isHidden(Step<?> step) {
        Class<?> clazz = step.getClass();
        while (clazz != null) {
            if (clazz.isAnnotationPresent(StepHidden.class)) {
                return true;
            }
            clazz = clazz.getSuperclass();
        }
        return false;
    }

    private void applyNodeAttributes(Step<?> task, Node node, boolean isRoot) {
        SimpleColor highlight = new SimpleColor();
        String background = colorForTask(task, selectedIdentifier);
        highlight.setBackgroundColor(background);
        String borderColor = borderForTask(task, selectedIdentifier);
        highlight.setBorderColor(borderColor.equals("darkgray") ? "black" : borderColor);

        node.setBorderWidth(2);
        node.setColor(NodeColor.builder().withBackground(background).withHighlightColor(highlight).withBorder(borderColor).build());
        node.setId(mainView.getQualifierInspector().getQualifierForBean(task));

        node.setClusterId(task.getClusterId());

        String label = null;
        if (task.getClass().isAnnotationPresent(StepName.class)) {
            StepName stepName = task.getClass().getAnnotation(StepName.class);
            label = stepName.value();
        } else {
            label = ViewUtils.splitCamelCase(task.getClass().getSimpleName());
        }
        String qualifier = mainView.getQualifierInspector().getQualifierForBean(task);
        if (qualifier.contains("_")) {
            label += " (" + qualifier.substring(qualifier.indexOf("_") + 1) + ")";
        }
        node.setLabel(label);
        if (isRoot) {
            node.setShape(Shape.star);
        } else {

            if (background.equals("white")) {
                String iconPath = getStepIcon(task);
                if (iconPath != null) {
                    node.setShape(Shape.circularImage);
                    node.setImage(iconPath);
                } else {
                    node.setShape(Shape.dot);
                }
            } else {
                node.setShape(Shape.dot);
            }
        }
        node.setSize(getStepSize(task));
    }

    public void saveNodePositions() {
        getNodePositions(nodes.stream().map(Node::getId).toList(), positions -> {
            mainView.getPositionsService().setPositions(positions);
            for (Node node : nodes) {
                NodePosition position = positions.get(node.getId());
                node.setX(position.x());
                node.setY(position.y());
                node.setPhysics(false);
            }
            nodeDataProvider.refreshAll();
        });
    }

    private String getTaskIdentifier(Step<?> task) {
        return mainView.getQualifierInspector().getQualifierForBean(task);
    }

    public void getNodePositions(List<String> nodeIds, Consumer<Map<String, NodePosition>> consumer) {
        nd.getPositions(nodeIds).then(jsonValue -> {
            Map<String, NodePosition> positions = new HashMap<>();
            String jsonString = jsonValue.toJson();
            // convert to JSON object using GSON

            try {
                Map<String, NodePosition> p = new ObjectMapper().readValue(jsonString, new TypeReference<Map<String, NodePosition>>() {
                    @Override
                    public Type getType() {
                        return super.getType();
                    }
                });
                p.keySet().forEach(key -> {
                    NodePosition pos = p.get(key);
                    positions.put(key, pos);
                });
                consumer.accept(positions);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }, e -> {
//            System.out.println(e);
        });
    }

    public void enablePhysics() {
        for (Node node : nodes) {
            node.setPhysics(true);
        }
        nodeDataProvider.refreshAll();
    }

    public void fitToView() {
        nd.diagramFit();
    }

    public void selectTask(Step<?> task) {
        nd.diagramSelectNodes(List.of(getTaskIdentifier(task)));
    }

    public void updateTask(Step<?> task) {
        Node node = nodes.stream().filter(n -> n.getId().equals(getTaskIdentifier(task))).findFirst().orElse(null);
        if (node != null) {
//            if (task.getClusterId() == 0 || clusters.isEmpty()) {
                applyNodeAttributes(task, node, mainView.getStepManager().getFirstSteps().stream().anyMatch(t -> t == task));
                nd.updateNode(node);
                if (!clusters.isEmpty() && task.getClusterId() > 0) {
                    updateClusterColorFor(task);
                }

//            } else {
//                updateClusterColorFor(task);
//            }
        }
    }

    private void updateClusterColorFor(Step<?> task) {
        StepStatus clusterStatus = clusters.get(task.getClusterId());
        if (clusterStatus != null) {
            StepStatus clusterNewStatus = calcClusterStatus(task.getClusterId());
            clusters.put(task.getClusterId(), clusterNewStatus);
            nd.updateClusterColor(task.getClusterId(), colorForStatus(clusterNewStatus), borderForStatus(clusterNewStatus), colorForStatus(clusterNewStatus));
        } else {
            StepStatus stepStatus = selectedIdentifier == null ? StepStatus.NOT_STARTED : task.getStatus(selectedIdentifier);
            clusters.put(task.getClusterId(), stepStatus);
            nd.updateClusterColor(task.getClusterId(), colorForStatus(stepStatus), borderForStatus(stepStatus), colorForStatus(stepStatus));
        }
    }

    private StepStatus calcClusterStatus(int clusterId) {
        if (selectedIdentifier == null) {
            return StepStatus.NOT_STARTED;
        }
        StepStatus clusterStatus = StepStatus.NOT_STARTED;
        if (mainView.getSelectedFlowSteps() == null) {
            return clusterStatus;
        }
        for (Step<?> step : mainView.getSelectedFlowSteps()) {
            if (step.getClusterId() == clusterId) {
                StepStatus status = selectedIdentifier != null ? step.getStatus(selectedIdentifier) : StepStatus.NOT_STARTED;
                clusterStatus = getWinningState(status, clusterStatus);
            }
        }
        return clusterStatus;
    }

    private String getStepIcon(Step<?> step) {
        Class<?> clazz = step.getClass();
        while (clazz != null) {
            if (clazz.isAnnotationPresent(StepIcon.class)) {
                StepIcon stepIcon = clazz.getAnnotation(StepIcon.class);
                return stepIcon.value();
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    public void cluster() {
        if (selectedRootTask != null && mainView.getSelectedFlowSteps() != null) {
            clusters.clear();
            for (Step<?> step : mainView.getSelectedFlowSteps()) {
                StepStatus status = selectedIdentifier != null ? step.getStatus(selectedIdentifier) : StepStatus.NOT_STARTED;
                StepStatus clusterStatus = clusters.get(step.getClusterId());
                if (step.getClusterId() > 0) {
                    if (clusterStatus == null) {
                        clusters.put(step.getClusterId(), status);
                    } else {
                        clusters.put(step.getClusterId(), getWinningState(status, clusterStatus));
                    }
                }

            }
            clusters.forEach((clusterId, status) -> {
                if (clusterId > 0) {
                    ClusterIdGenerator.ClusterDetails details = ClusterIdGenerator.getClusterdetails(clusterId);
                    nd.clusterByClusterId(clusterId, 1000, details.getName(), details.getIconPath(), colorForStatus(status), borderForStatus(status), colorForStatus(status));
                }
            });
            expandedState = false;
        }

    }

    private StepStatus getWinningState(StepStatus status, StepStatus clusterStatus) {
        if (clusterStatus == StepStatus.NOT_STARTED) {
            return status;
        }
        if (status == StepStatus.RUNNING) {
            return StepStatus.RUNNING;
        }
        if (status == StepStatus.REWINDING) {
            return StepStatus.REWINDING;
        }
        if (status == StepStatus.FAILED && clusterStatus != StepStatus.RUNNING) {
            return StepStatus.FAILED;
        }
        if (status == StepStatus.SUCCESS && clusterStatus != StepStatus.RUNNING) {
            return StepStatus.SUCCESS;
        }

        return clusterStatus;
    }

    public void openClusters() {
        if (selectedRootTask != null && mainView.getSelectedFlowSteps() != null) {
            Set<Integer> clusterIds = new HashSet<>();
            for (Step<?> step : mainView.getSelectedFlowSteps()) {
                clusterIds.add(step.getClusterId());
            }
            clusterIds.forEach(clusterId -> {
                if (clusterId > 0) {
                    nd.openCluster("cluster_" + clusterId);
                }
            });
            clusters.clear();
        }
        expandedState = true;
    }

    private int getStepSize(Step<?> step) {
        Class<?> clazz = step.getClass();
        while (clazz != null) {
            if (clazz.isAnnotationPresent(StepSize.class)) {
                StepSize stepIcon = clazz.getAnnotation(StepSize.class);
                return stepIcon.value();
            }
            clazz = clazz.getSuperclass();
        }
        return 20;
    }
}
