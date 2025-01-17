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

import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import lombok.Getter;
import org.beanrunner.core.*;
import org.beanrunner.core.annotations.*;
import org.beanrunner.core.logging.CustomSpringLogbackAppender;
import org.beanrunner.core.logging.LogEvent;
import org.beanrunner.core.settings.ConfigurationSettings;
import org.beanrunner.core.settings.SettingsManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.vaadin.addons.visjs.network.main.Edge;
import org.vaadin.addons.visjs.network.main.NetworkDiagram;
import org.vaadin.addons.visjs.network.main.Node;
import org.vaadin.addons.visjs.network.options.Options;
import org.vaadin.addons.visjs.network.options.nodes.NodeColor;
import org.vaadin.addons.visjs.network.options.nodes.Nodes;
import org.vaadin.addons.visjs.network.options.physics.BarnesHut;
import org.vaadin.addons.visjs.network.options.physics.Physics;
import org.vaadin.addons.visjs.network.options.physics.Repulsion;
import org.vaadin.addons.visjs.network.options.physics.Stabilization;
import org.vaadin.addons.visjs.network.util.SimpleColor;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

@Route("")
@JsModule("./js/copytoclipboard.js")
public class MainView extends VerticalLayout implements StepListener, HasDynamicTitle {

    @Getter
    private final QualifierInspector qualifierInspector;

    private final Grid<Step<?>> gridTasks = new Grid<>((Class<Step<?>>) (Class<?>) Step.class, false);
    private final VerticalLayout pnlContent = new VerticalLayout();
    private final String displayName;
    private FlowRunIdentifier selectedIdentifier;
    private DataProvider<Step<?>, ?> dataProvider;
    private Step<?> selectedStep;

    private List<Step<?>> tasks;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    @Getter
    private final StepManager stepManager;
    @Getter
    private final PositionsService positionsService;
    private final TaskTreeView pnlTreeView;

    private final Grid<FlowRunIdentifier> gridIdentifiers = new Grid<>(FlowRunIdentifier.class, false);

    private final SplitLayout splitInner = new SplitLayout();
    private final SplitLayout splitRight = new SplitLayout();
    private final SplitLayout splitLeft = new SplitLayout();
    private final SplitLayout splitTopRight = new SplitLayout();

    private Step<?> selectedFlow;

    @Getter
    private List<Step<?>> selectedFlowSteps;

    private final List<FlowRunIdentifier> identifiers = new ArrayList<>();
    private final ListDataProvider<FlowRunIdentifier> identifierDataProvider = DataProvider.ofCollection(identifiers);
    private final LogsView logsView;
    private final CustomSpringLogbackAppender appender;
    private final VerticalLayout pnlTaskDetails = new VerticalLayout();

    private final TreeGrid<RootStepProfile> gridProfiles = new TreeGrid<>(RootStepProfile.class, false);

    private final VerticalTabSheet tabTaskDetails = new VerticalTabSheet();
    private final TextArea txtTaskData = new TextArea();
    private final Span spanTaskName = new Span();
    private final Span spanTaskDescription = new Span();
    private final HorizontalLayout pnlTaskDetailsHeader = new HorizontalLayout();
    private SplitLayout splitLayout;
    private final CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.SPRING));
    private final HorizontalLayout pnlCronDescription = new HorizontalLayout();
    private final Span spanCron = new Span();
    private final SettingsManager settingsManager;
    private final ConfigurationView pnlConfiguration;
    private final HorizontalLayout pnlTasksHeader = new HorizontalLayout();
    private final TextField txtFilterTasks = new TextField();
    private final List<Node> nodes = new ArrayList<>();
    private final ListDataProvider<Node> nodeDataProvider = new ListDataProvider<>(nodes);
    private final List<Edge> edges = new ArrayList<>();
    private final ListDataProvider<Edge> edgeDataProvider = new ListDataProvider<>(edges);
    private final DiagramView diagramView;
    private boolean isGraphLocked = false;
    private Tab tabTasks;
    private Tab tabConfig;

    private Tabs tabs;
    private NetworkDiagram nd;

    private Span spanSelectedTaskName = new Span();
    private TextArea txtSelectedTaskDescription = new TextArea();
    private VerticalLayout pnlConfigureTaskSettings = new VerticalLayout();
    private ComboBox<RootStepProfile> cmbProfiles = new ComboBox<>();

    private Debouncer updateTasksDebouncer = new Debouncer(2000);
    private Debouncer updateIRunIdentifierDebouncer = new Debouncer(3000);

    private Button btnRewind = new Button("Rewind", VaadinIcon.REPLY.create());
    private Button btnExpandCollapse = new Button("Expand", VaadinIcon.EXPAND.create());

    @Override
    public String getPageTitle() {
        return displayName;
    }

    public void diagramTaskSelected(Step<?> task) {
        selectedStep = task;
        if (task != null) {
            updateTaskSidePanel(task);

            if (selectedIdentifier != null) {
                Object data = task.getData(selectedIdentifier);
                if (data != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    try {
                        txtTaskData.setValue(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
                        txtTaskData.setVisible(true);
                    } catch (Exception e) {
                        txtTaskData.setValue(data.toString());
                        txtTaskData.setVisible(false);
                    }
                } else {
                    txtTaskData.setValue("");
                    txtTaskData.setVisible(false);
                }

                List<LogEvent> events = appender.getEvents(qualifierInspector.getQualifierForBean(task), selectedIdentifier.getId());
                logsView.setLogEvents(task, selectedIdentifier, events);
            } else {
                txtTaskData.setVisible(false);
            }

            diagramView.selectTask(task);
        } else {
            logsView.clear();
        }
        updateTaskSidePanel(task);
    }

    record Position(int x, int y) {
    }

    record Positions(Map<String, Position> positions) {
    }

    public MainView(@Autowired StepManager stepManager,
                    @Autowired CustomSpringLogbackAppender appender,
                    @Autowired SettingsManager settingsManager,
                    @Autowired PositionsService positionsService,
                    @Autowired QualifierInspector qualifierInspector,
                    @Value("${bean-runner.display-name:BeanRunner}") String displayName,
                    @Value("${bean-runner.icon-path:images/bean-runner-logo.svg}") String iconPath) {
        this.qualifierInspector = qualifierInspector;
        this.stepManager = stepManager;
        this.settingsManager = settingsManager;
        this.pnlConfiguration = new ConfigurationView(settingsManager);
        this.positionsService = positionsService;
        this.appender = appender;
        this.pnlTreeView = new TaskTreeView(stepManager, this);
        this.diagramView = new DiagramView(this);
        this.displayName = displayName;
        this.logsView = new LogsView(this);
        setSizeFull();
        setPadding(false);
        setMargin(false);
        setSpacing(false);

        HorizontalLayout navBar = new HorizontalLayout();

        navBar.setAlignItems(Alignment.CENTER);
        navBar.setWidthFull();
        navBar.setClassName("navbar");

        Image imgCoyote = new Image("images/bean-runner-logo.svg", "BeanRunner");
        imgCoyote.setClassName("rotating-icon");
        imgCoyote.setWidth("40px");
        imgCoyote.setHeight("40px");
        Span lblCoyote = new Span(displayName);
        lblCoyote.getStyle().set("font-size", "20px");
        lblCoyote.getStyle().set("font-weight", "bold");
        navBar.add(imgCoyote);
        navBar.add(lblCoyote);

        Span filler = new Span();
        navBar.add(filler);
        navBar.setFlexGrow(1, filler);

        HorizontalLayout layoutTasks = new HorizontalLayout();
        layoutTasks.setAlignItems(Alignment.CENTER);
        Image imgTasks = new Image("images/graph.svg", "Tasks");
        imgTasks.setWidth("25px");
        imgTasks.setHeight("25px");

        layoutTasks.add(new Span("Tasks"));

        HorizontalLayout layoutConfig = new HorizontalLayout();
        layoutTasks.setAlignItems(Alignment.CENTER);
        Icon iconConfig = VaadinIcon.COG.create();
        iconConfig.setSize("25px");
        layoutConfig.add(new Span("Configuration"));

        tabTasks = new Tab(layoutTasks);
        tabConfig = new Tab("Configuration");
        tabs = new Tabs(tabTasks, tabConfig);

//        navBar.add(tabs);
        tabTasks.setClassName("main-tab");
        tabConfig.setClassName("main-tab");

        tabs.addSelectedChangeListener(e -> {
            splitLayout.setVisible(false);
            pnlConfiguration.setVisible(false);
            if (e.getSelectedTab() == tabTasks) {
                splitLayout.setVisible(true);
            } else if (e.getSelectedTab() == tabConfig) {
                pnlConfiguration.setVisible(true);
            }
        });

        add(navBar);

        gridTasks.addComponentColumn(t -> {
            HorizontalLayout layout = new HorizontalLayout();

            layout.setAlignItems(Alignment.CENTER);

            Image img = new Image("images/bean-runner-logo.svg", "");
            img.setWidth("20px");
            img.setHeight("20px");
//            if (t.getClass().isAnnotationPresent(StepCategory.class)) {
//                layout.add(VaadinIcon.STAR.create());
//            } else {
                layout.add(img);
//            }

            String name = ViewUtils.splitCamelCase(t.getClass().getSimpleName());
            if (t.getClass().isAnnotationPresent(StepName.class)) {
                StepName stepName = t.getClass().getAnnotation(StepName.class);
                name = stepName.value();
            }

            layout.add(new Span(name));


            Span spacer = new Span();
            layout.add(spacer);
            layout.setFlexGrow(1, spacer);

            for (FlowRunIdentifier identifier : t.getIdentifiers()) {
                if (identifier.isRunning()) {
                    Loader icon = new Loader("loader-running");
                    layout.add(icon);
                    break;
                }
            }

            if (t.getClass().isAnnotationPresent(StepSchedule.class)) {
                ToggleButton btnCronEnabled = new ToggleButton("", VaadinIcon.CLOCK.create());
                if (stepManager.isCronEnabled(t)) {
                    btnCronEnabled.setToggled(true);
                } else {
                    btnCronEnabled.setToggled(false);
                }
                btnCronEnabled.addClickListener(e -> {
                    stepManager.setCronEnabled(t, btnCronEnabled.isToggled());
                });

                layout.add(btnCronEnabled);
            }

            layout.add(new Button(VaadinIcon.PLAY.create(), e -> {
                executeTask(t);
            }));
            return layout;
        });

        gridTasks.addSelectionListener(e -> {
            if (e.getFirstSelectedItem().isPresent()) {

                pnlTreeView.clear();
                selectedFlow = e.getFirstSelectedItem().get();
                selectedFlowSteps = stepManager.flattenSteps(selectedFlow);
                stepManager.loadFlowIdentifiersFromStorageIfNecessary(selectedFlow);

                identifiers.clear();
                identifiers.addAll(e.getFirstSelectedItem().get().getIdentifiers());
                identifiers.sort((o1, o2) -> Long.compare(o2.getTimestamp(), o1.getTimestamp()));

                identifierDataProvider.refreshAll();

                if (!identifiers.isEmpty()) {
                    gridIdentifiers.deselectAll();
                }

                if (selectedFlow.getClass().isAnnotationPresent(StepName.class)) {
                    StepName name = selectedFlow.getClass().getAnnotation(StepName.class);
                    spanTaskName.setText(name.value());
                } else {
                    spanTaskName.setText(selectedFlow.getClass().getSimpleName());
                }
                spanTaskDescription.setText("");

                if (selectedFlow.getClass().isAnnotationPresent(StepSchedule.class)) {
                    StepSchedule runAt = selectedFlow.getClass().getAnnotation(StepSchedule.class);
                    Cron cron = parser.parse(runAt.value());
                    CronDescriptor descriptor = CronDescriptor.instance(Locale.ENGLISH);
                    String description = descriptor.describe(cron);
                    spanCron.setText(description);
                    pnlCronDescription.setVisible(true);
                } else {
                    pnlCronDescription.setVisible(false);
                    spanCron.setText("");
                }

            } else {
                txtSelectedTaskDescription.setValue("");
                spanSelectedTaskName.setText("");
            }
            updateTaskSidePanel(null);
            pnlTreeView.setRootTask(selectedFlow, selectedIdentifier);

            WebStorage.getItem("expandedState_" + selectedFlow.getClass().getName()).thenAccept(value -> {
                diagramView.setSelectedFlow(selectedFlow, selectedIdentifier);
                if (selectedFlowSteps.stream().anyMatch(t -> t.getClusterId() > 0)) {
                    btnExpandCollapse.setVisible(true);
                    if (value == null || value.equals("collapsed")) {
                        diagramView.cluster();
                        btnExpandCollapse.setIcon(VaadinIcon.EXPAND_SQUARE.create());
                        btnExpandCollapse.setText("Expand");
                    } else if (value.equals("expanded")) {
                        btnExpandCollapse.setIcon(VaadinIcon.COMPRESS_SQUARE.create());
                        btnExpandCollapse.setText("Collapse");
                        diagramView.openClusters();
                    }
                } else if (value != null && value.equals("expanded")) {
                    btnExpandCollapse.setVisible(false);
                    btnExpandCollapse.setIcon(VaadinIcon.COMPRESS_SQUARE.create());
                    btnExpandCollapse.setText("Collapse");
                    diagramView.openClusters();
                }
            });

        });

        gridTasks.setSizeFull();

        splitLayout = new SplitLayout();
        splitLayout.setSizeFull();
        splitLayout.addToPrimary(splitLeft);

        splitInner.setOrientation(SplitLayout.Orientation.HORIZONTAL);
        splitInner.setSizeFull();
        gridIdentifiers.setDataProvider(identifierDataProvider);
        gridIdentifiers.setSizeFull();
        gridIdentifiers.addComponentColumn(i -> {
            VerticalLayout verticalLayout = new VerticalLayout();
            verticalLayout.setWidthFull();

            HorizontalLayout layout = new HorizontalLayout();

            LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(i.getTimestamp()),
                    TimeZone.getDefault().toZoneId());
            String timeString = formatter.format(dateTime);

            if (i.getSourceIconPath() != null) {
                Image sourceIcon = new Image(i.getSourceIconPath(), i.getSourceName());
                sourceIcon.setWidth("20px");
                sourceIcon.setHeight("20px");
                layout.add(sourceIcon);
            } else {
                Icon sourceIcon = VaadinIcon.COG.create();
                sourceIcon.setSize("20px");
                layout.add(sourceIcon);
            }

            layout.setWidthFull();
            layout.add(new Span(timeString));

            Span filler2 = new Span();
            layout.add(filler2);
            layout.setAlignItems(Alignment.CENTER);
            layout.setFlexGrow(1, filler2);

            StepStatus status = null;
            if (i.isOverrideDisplayValues()) {
                status = i.getFlowStatus();
            } else {
                status = getFlowStatus(i);
            }

            if (i.isRewindArmed()) {
                Icon icon = VaadinIcon.CIRCLE.create();
                icon.setColor("#99ff6e");
                layout.add(icon);
            } else if (status == StepStatus.READY || status == StepStatus.RUNNING) {
                Loader icon = new Loader("loader-running");
                layout.add(icon);
            } else if (status == StepStatus.PENDING_REWIND || status == StepStatus.REWINDING) {
                Loader icon = new Loader("loader-rewinding");
                layout.add(icon);
            } else if (status == StepStatus.SUCCESS) {
                Icon icon = VaadinIcon.CHECK.create();
                icon.setColor("green");
                icon.setSize("20px");
                layout.add(icon);
            } else if (status == StepStatus.FAILED) {
                Icon icon = VaadinIcon.CLOSE.create();
                icon.setColor("red");
                icon.setSize("20px");
                layout.add(icon);
            }

            verticalLayout.add(layout);

            // tags
            List<TaskTagItem> tagItems = getTagsFor(i);

            if (tagItems != null && !tagItems.isEmpty()) {
                MultiSelectComboBox<TaskTagItem> comboBox = new MultiSelectComboBox<>();

                comboBox.setItems(tagItems);
                comboBox.select(tagItems);
                comboBox.setReadOnly(true);
                comboBox.setItemLabelGenerator(TaskTagItem::getLabel);
                comboBox.setAutoExpand(MultiSelectComboBox.AutoExpandMode.BOTH);
                comboBox.setWidthFull();
                comboBox.setClassNameGenerator(TaskTagItem::getClassName);
                verticalLayout.add(comboBox);
            }

            // properties
            i.getRunProperties().forEach((key, value) -> {
                HorizontalLayout propertyLayout = new HorizontalLayout();
                propertyLayout.setAlignItems(Alignment.CENTER);
                Span lblKey = new Span(key + ": ");
                lblKey.getStyle().setFontWeight("bold");
                propertyLayout.add(lblKey);
                Span lblValue = new Span(value);
                propertyLayout.add(lblValue);
                lblKey.getStyle().setFontSize("12px");
                lblValue.getStyle().setFontSize("12px");
                verticalLayout.add(propertyLayout);
            });

            return verticalLayout;
        });
        gridIdentifiers.addSelectionListener(e -> {
            if (e.getFirstSelectedItem().isPresent()) {
                selectedIdentifier = e.getFirstSelectedItem().get();
                stepManager.loadAndPropagateIdentifierIfNecessary(selectedFlow, selectedIdentifier);
                identifierDataProvider.refreshAll();
                if (selectedFlow != null) {
                    FlowRunIdentifier identifier = e.getFirstSelectedItem().get();
                    pnlTreeView.setRootTask(selectedFlow, identifier);
                    diagramView.setSelectedFlow(selectedFlow, identifier);
                }
                btnRewind.setVisible(selectedIdentifier.isRewindArmed());
            } else {
                selectedIdentifier = null;
                diagramView.setSelectedFlow(selectedFlow, null);
                btnRewind.setVisible(false);
            }
            if (selectedStep != null && selectedIdentifier != null) {
                logsView.setLogEvents(selectedStep, selectedIdentifier, appender.getEvents(qualifierInspector.getQualifierForBean(selectedStep), selectedIdentifier.getId()));
            } else {
                logsView.clear();
            }
        });
//        splitInner.addToPrimary(gridIdentifiers);

        splitRight.setSplitterPosition(50);
        splitRight.setOrientation(SplitLayout.Orientation.VERTICAL);

        VerticalLayout pnlTreeViewWrapper = new VerticalLayout();

        pnlTreeViewWrapper.setPadding(false);
        pnlTreeViewWrapper.setMargin(false);
        pnlTreeViewWrapper.setSpacing(false);

        spanTaskName.getStyle().setFontSize("20px");
        spanTaskName.getStyle().setFontWeight("bold");
        spanTaskDescription.getStyle().setFontSize("14px");
        pnlTaskDetailsHeader.setAlignItems(Alignment.CENTER);

        pnlTaskDetailsHeader.setWidthFull();
        pnlTaskDetailsHeader.setClassName("task-details-header");
        pnlTaskDetailsHeader.add(spanTaskName);
        pnlTaskDetailsHeader.add(spanTaskDescription);
        pnlTaskDetailsHeader.setHeight("55px");

        filler = new Span();
        pnlTaskDetailsHeader.add(filler);
        pnlTaskDetailsHeader.setFlexGrow(1, filler);

//        pnlTaskDetailsHeader.add(pnlCronDescription);

        pnlCronDescription.setVisible(false);
//        pnlCronDescription.setWidthFull();
        pnlCronDescription.setClassName("cron-description");
        Icon iconClock = VaadinIcon.CLOCK.create();
        iconClock.setSize("20px");
        pnlCronDescription.setAlignItems(Alignment.CENTER);
        pnlCronDescription.add(iconClock);
        pnlCronDescription.add(spanCron);

        ToggleButton btnShowHideLogs = new ToggleButton("", VaadinIcon.LIST.create());
        btnShowHideLogs.addClickListener(e -> {
            if (btnShowHideLogs.isToggled()) {
                logsView.setVisible(true);
            } else {
                logsView.setVisible(false);
            }

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    getUI().ifPresent(ui -> ui.access(diagramView::fitToView));
                }
            }, 300);
        });

        ToggleButton btnShowHideSidePanel = new ToggleButton("", VaadinIcon.INFO_CIRCLE.create());
        btnShowHideSidePanel.addClickListener(e -> {
            if (btnShowHideSidePanel.isToggled()) {
                tabTaskDetails.setVisible(true);
            } else {
                tabTaskDetails.setVisible(false);
            }
        });
        btnShowHideSidePanel.setToggled(true);
        btnShowHideLogs.setToggled(true);


//        pnlTreeViewWrapper.add(pnlTaskDetailsHeader);
//        pnlTreeView.setSizeFull();

        pnlTreeViewWrapper.add(pnlTreeView.getGrid());

        splitTopRight.setOrientation(SplitLayout.Orientation.HORIZONTAL);
        splitTopRight.setSplitterPosition(70);
        splitTopRight.setSizeFull();

        diagramView.getElement().addEventListener("keydown", event -> {
            String key = event.getEventData().getString("event.key");
            String code = event.getEventData().getString("event.code");
            if (key.equals("g") || key.equals("G")) {
                toggleExpandedState();
            } else if (key.equals("f") || key.equals("F")) {
                diagramView.fitToView();
            }
        }).addEventData("event.key").addEventData("event.code"); // Include event data

        splitTopRight.addToPrimary(diagramView);
//        pnlTaskDetails.setSizeFull();
        pnlTaskDetails.getStyle().setPaddingRight("30px");

        tabTaskDetails.add(VaadinIcon.INFO.create(), pnlTaskDetails);

        tabTaskDetails.setSizeFull();

        HorizontalLayout pnlTabs = new HorizontalLayout();
        pnlTabs.setAlignItems(Alignment.CENTER);
        pnlTabs.setWidthFull();
        pnlTabs.setPadding(false);
        pnlTabs.setMargin(false);

        filler = new Span();
        pnlTabs.add(filler);
        pnlTabs.setFlexGrow(1, filler);

        Button btnFitToView = new Button(VaadinIcon.VIEWPORT.create());
        Image imgPin = new Image("images/pin.svg", "Save positions");
        imgPin.setWidth("20px");
        imgPin.setHeight("20px");

        Button btnSavePositions = new Button(imgPin);
        btnSavePositions.addClickListener(e -> {
            diagramView.saveNodePositions();
        });

        Image imgUnpin = new Image("images/unpin.svg", "Unpin All");
        imgUnpin.setWidth("20px");
        imgUnpin.setHeight("20px");

        Button btnEnablePhysics = new Button(imgUnpin);
        btnEnablePhysics.addClickListener(e -> {
            diagramView.enablePhysics();
        });

        btnRewind.setClassName("rewind-button");
        btnRewind.setVisible(false);

        btnRewind.addClickListener(e -> {
            rewindAll();
        });

        btnExpandCollapse.addClickListener(e -> {
            toggleExpandedState();
        });
        btnExpandCollapse.setVisible(false);
        btnExpandCollapse.setMinWidth("120px");
        btnExpandCollapse.setWidth("120px");

        pnlTaskDetailsHeader.add(btnRewind);
        pnlTaskDetailsHeader.add(btnExpandCollapse);

        pnlTaskDetailsHeader.add(btnShowHideLogs);
        pnlTaskDetailsHeader.add(btnShowHideSidePanel);

        Button btnCopyPositionsToClipboard = new Button(VaadinIcon.COPY.create(), e -> {
            UI.getCurrent().getPage().executeJs("window.copyToClipboard($0)", positionsService.getPositionsJson());
            Notification.show("Positions JSON copied to clipboard");
        });

        Button btnRestoreDefaultPositions = new Button(VaadinIcon.DOT_CIRCLE.create(), e -> {
            positionsService.loadDefaultPositions();
            Notification.show("Default positions restored");
            diagramView.populateGraphDiagram();
        });

        btnCopyPositionsToClipboard.setTooltipText("Copy positions to clipboard");
        btnRestoreDefaultPositions.setTooltipText("Restore default positions");
        btnSavePositions.setTooltipText("Pin nodes and save positions");
        btnEnablePhysics.setTooltipText("Allow nodes to move freely");
        btnFitToView.setTooltipText("Fit to view");

        MenuBar menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_ICON);
        MenuItem itemPositions = menuBar.addItem(VaadinIcon.MENU.create(), "Positions");
        itemPositions.getSubMenu().addItem("Copy positions to clipboard", e -> {
            UI.getCurrent().getPage().executeJs("window.copyToClipboard($0)", positionsService.getPositionsJson());
            Dialogs.info("""
                    All task node Positions have been copied to clipboard.
                    Paste the JSON into a default_positions.json file in the project's resources folder.
                    This will make the task diagram positions available to other developers when they run the application locally for the first time.""");
        });

        itemPositions.getSubMenu().addItem("Restore default positions", e -> {
            Dialogs.confirm("Restore default positions", "This will restore positions in ALL your flows. Continue?", "Yes", () -> {
                positionsService.loadDefaultPositions();
                Notification.show("Default positions restored");
                diagramView.populateGraphDiagram();
            });
        });

        itemPositions.getSubMenu().addItem("Allow nodes to move freely", e -> {
            Dialogs.confirm("Allow nodes to move freely", "Only use this when rearranging the entire diagram, as all nodes will move. Continue?", "Yes", diagramView::enablePhysics);
        });
//        pnlTaskDetailsHeader.add(btnCopyPositionsToClipboard);
//        pnlTaskDetailsHeader.add(btnRestoreDefaultPositions);
//        pnlTaskDetailsHeader.add(btnEnablePhysics);
        pnlTaskDetailsHeader.add(btnSavePositions);
        pnlTaskDetailsHeader.add(btnFitToView);

        pnlTaskDetailsHeader.add(menuBar);

        btnFitToView.addClickListener(e -> {
            diagramView.fitToView();
        });

        txtTaskData.setSizeFull();
        txtTaskData.setReadOnly(true);
//        pnlTaskDetails.add(pnlCronDescription);

        spanSelectedTaskName.getStyle().setFontSize("20px");
        spanSelectedTaskName.getStyle().setFontWeight("bold");

        txtSelectedTaskDescription.setReadOnly(true);
        txtSelectedTaskDescription.setSizeFull();

        pnlTaskDetails.add(spanSelectedTaskName);
        pnlTaskDetails.add(txtSelectedTaskDescription);
        pnlTaskDetails.add(pnlConfigureTaskSettings);
        pnlTaskDetails.add(txtTaskData);
        pnlConfigureTaskSettings.setPadding(false);
        pnlConfigureTaskSettings.setMargin(false);
        pnlConfigureTaskSettings.setWidthFull();


        Physics physics = new Physics();

        BarnesHut barnesHut = new BarnesHut();
        barnesHut.setAvoidOverlap(0.2f);
        barnesHut.setCentralGravity(0.3f);
        barnesHut.setDamping(0.09f);
        barnesHut.setSpringLength(100);
        physics.setBarnesHut(barnesHut);
        physics.setSolver(Physics.Solver.repulsion);

        Repulsion repulsion = new Repulsion();
        repulsion.setNodeDistance(300);
        physics.setRepulsion(repulsion);
        Stabilization stabilization = new Stabilization();
        stabilization.setIterations(1);
        stabilization.setFit(true);
//        stabilization.setEnabled(false);

        stabilization.setEnabled(true);


        SimpleColor highlightColor = new SimpleColor();
//        highlightColor.setBackgroundColor("transparent");
        highlightColor.setBorderColor("black");
        setPadding(false);
        setMargin(false);
        physics.setStabilization(stabilization);
        nd =
                new NetworkDiagram(Options.builder()
                        .withAutoResize(true)
                        .withNodes(Nodes.builder().withSize(20).withColor(NodeColor.builder().withHighlightColor(highlightColor).build())
                                .build()).build());
        nd.setSizeFull();
//        nd.getStyle().setBackground("#607ba6");
        nd.addStabilizedListener(ls -> {
//           lock();
        });

        nd.addSelectNodeListener(ls -> {
            String nodeId = ls.getParams().getArray("nodes").getString(0);
            for (Step<?> task : stepManager.getAllSteps()) {
                if (task.getClass().getName().equals(nodeId)) {
                    pnlTreeView.setSelectedTask(task);
                    break;
                }
            }
        });

        nd.setNodesDataProvider(nodeDataProvider);
        nd.setEdgesDataProvider(edgeDataProvider);
        pnlTaskDetails.setPadding(true);
        pnlTaskDetails.setMargin(true);


//        pnlTaskDetails.add(new Span("Step Data"));
//        pnlTaskDetails.add(txtTaskData);

        splitTopRight.addToSecondary(tabTaskDetails);

        VerticalLayout pnlTopRight = new VerticalLayout();
        pnlTopRight.setSizeFull();
        pnlTopRight.setPadding(false);
        pnlTopRight.setMargin(false);
        pnlTopRight.setSpacing(false);
        pnlTopRight.add(pnlTaskDetailsHeader);

        pnlTopRight.add(splitTopRight);

        splitRight.addToPrimary(pnlTopRight);
        splitRight.addToSecondary(logsView);

        splitLeft.setSplitterPosition(50);
        splitLeft.setOrientation(SplitLayout.Orientation.VERTICAL);

        VerticalLayout pnlTasks = new VerticalLayout();
        pnlTasks.setPadding(false);
        pnlTasks.setMargin(false);
        pnlTasks.setSpacing(false);
        pnlTasks.add(pnlTasksHeader);
        pnlTasks.add(gridTasks);
        pnlTasksHeader.setWidthFull();
        pnlTasksHeader.setClassName("tasks-header");
        pnlTasksHeader.setAlignItems(Alignment.CENTER);
        Span spanTasks = new Span("Flows");
        spanTasks.getStyle().setFontSize("20px").setFontWeight("bold");
        pnlTasksHeader.add(spanTasks);

        txtFilterTasks.setPlaceholder("Filter tasks");
        txtFilterTasks.setWidthFull();
        txtFilterTasks.setClearButtonVisible(true);
        txtFilterTasks.addValueChangeListener(e -> {
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                tasks.clear();
                tasks.addAll(stepManager.getFirstSteps().stream().filter(t -> t.getClass().getSimpleName().toLowerCase().contains(e.getValue().toLowerCase())).toList());
                dataProvider.refreshAll();

            } else {
                tasks.clear();
                tasks.addAll(stepManager.getFirstSteps());
                dataProvider.refreshAll();
            }

        });
//        pnlTasksHeader.add(txtFilterTasks);
        splitLeft.addToPrimary(pnlTasks);

        HorizontalLayout pnlIdentifiersHeader = new HorizontalLayout();
        pnlIdentifiersHeader.setPadding(true);
        pnlIdentifiersHeader.setWidthFull();
        pnlIdentifiersHeader.setClassName("identifiers-header");
        pnlIdentifiersHeader.setAlignItems(Alignment.CENTER);
        Span spanIdentifiers = new Span("Runs");
        spanIdentifiers.getStyle().setFontSize("20px").setFontWeight("bold");

        pnlIdentifiersHeader.add(spanIdentifiers);
        Span identifiersPanelFiller = new Span();
        pnlIdentifiersHeader.add(identifiersPanelFiller);
        pnlIdentifiersHeader.setFlexGrow(1, spanIdentifiers);

        ToggleButton btnFilterOutNonTagged = new ToggleButton("", VaadinIcon.TAGS.create());
        btnFilterOutNonTagged.setTooltipText("Show only tagged runs");
        btnFilterOutNonTagged.addClickListener(e -> {
            if (btnFilterOutNonTagged.isToggled()) {
                identifierDataProvider.setFilter(run -> ! getTagsFor(run).isEmpty());
            } else {
                identifierDataProvider.setFilter(null);
            }
        });

        pnlIdentifiersHeader.add(btnFilterOutNonTagged);

        VerticalLayout pnlIdentifiers = new VerticalLayout();
        pnlIdentifiers.setPadding(false);
        pnlIdentifiers.setMargin(false);
        pnlIdentifiers.setSpacing(false);
        pnlIdentifiers.add(pnlIdentifiersHeader);
        pnlIdentifiersHeader.setWidthFull();

        pnlIdentifiers.add(gridIdentifiers);

        splitLeft.addToSecondary(pnlIdentifiers);

//        splitInner.addToSecondary(splitRight);

        splitLayout.addToSecondary(splitRight);
        splitLayout.setOrientation(SplitLayout.Orientation.HORIZONTAL);
        splitLayout.setSplitterPosition(23);
        splitInner.setSplitterPosition(20);
        add(splitLayout);
        pnlConfiguration.setVisible(false);
        add(pnlConfiguration);

        tasks = stepManager.getFirstSteps();

        dataProvider = DataProvider.ofCollection(tasks);
        gridTasks.setDataProvider(dataProvider);

    }

    private void rewindAll() {
        Dialogs.confirm("Rewind", "Are you sure you want to rewind this flow?", "Yes", () -> {
            stepManager.rewindAllRewindableSteps(selectedFlow, selectedIdentifier);
            selectedIdentifier.setRewindArmed(false);
            selectedIdentifier.setOverrideDisplayValues(false);
            getUI().ifPresent(ui -> ui.access(() -> {
                btnRewind.setVisible(false);
                identifierDataProvider.refreshAll();
            }));
        });
    }

    private void toggleExpandedState() {
        if (diagramView.isExpandedState()) {
            btnExpandCollapse.setIcon(VaadinIcon.EXPAND_SQUARE.create());
            btnExpandCollapse.setText("Expand");
            diagramView.cluster();
            WebStorage.setItem("expandedState_" + selectedFlow.getClass().getName(), "collapsed");
        } else {
            btnExpandCollapse.setIcon(VaadinIcon.COMPRESS_SQUARE.create());
            btnExpandCollapse.setText("Collapse");
            diagramView.openClusters();
            WebStorage.setItem("expandedState_" + selectedFlow.getClass().getName(), "expanded");
        }
    }

    private <D> void executeTask(Step<D> t) {
        Dialog dialog = new Dialog();

        AtomicReference<D> param = new AtomicReference<>();
        Type type = t.getClass().getGenericSuperclass();

        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;

            // Get the actual type arguments
            Type[] typeArguments = parameterizedType.getActualTypeArguments();

            Type parameterType = typeArguments[0];

            if (parameterType instanceof Class) {
                Class<D> clazz = (Class<D>) parameterType;
                try {
                    param.set((D) clazz.getDeclaredConstructor().newInstance());
                    VerticalLayout pnlProperties = buildPropertiesUI(param.get(), () -> {
                    });
                    pnlProperties.getChildren().findFirst().ifPresent(c -> {
                        if (c instanceof TextField) {
                            ((TextField) c).focus();
                        }
                    });
                    dialog.add(pnlProperties);
                } catch (Exception ex) {

                }
            }
        }

        dialog.getFooter().add(new Button("Cancel", e2 -> dialog.close()));
        if (param.get() == null) {
            stepManager.executeFlow(t, null, false, "Manual", "images/source-manual.svg");
        } else {
            Button btnRun = new Button("Run", e2 -> {
                dialog.close();
                stepManager.executeFlow(t, param.get(), false, "Manual", "images/source-manual.svg");
            });
            btnRun.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            btnRun.addClickShortcut(Key.ENTER);
            dialog.getFooter().add(btnRun);
            dialog.setWidth("600px");
            dialog.open();
        }
    }

    private List<TaskTagItem> getTagsFor(FlowRunIdentifier identifier) {
        if (selectedFlow == null || identifier == null) {
            return List.of();
        }
        if (identifier.getTags() != null && !identifier.getTags().isEmpty()) {
            return identifier.getTags();
        }
        return selectedFlowSteps.stream().flatMap(task -> {
            if (task.getStatus(identifier) != StepStatus.NOT_STARTED
                    && task.getStatus(identifier) != StepStatus.FAILED_TRANSITIVELY) {
                if (task.getClass().isAnnotationPresent(StepTag.class)) {
                    StepTag tag = task.getClass().getAnnotation(StepTag.class);
                    return Stream.of(new TaskTagItem(tag));
                }
            }
            return Stream.empty();
        }).toList();
    }

    private String colorForTask(Step<?> task, FlowRunIdentifier identifier) {
        if (identifier == null) {
            return "white";
        }
        StepStatus status = task.getStatus(identifier);
        if (status == StepStatus.RUNNING) {
            return "yellow";
        } else if (status == StepStatus.SUCCESS) {
            return "lightgreen";
        } else if (status == StepStatus.FAILED) {
            return "red";
        } else if (status == StepStatus.FAILED_TRANSITIVELY) {
            return "#ffb8be";
        }
        return "white";
    }

    private void updateTaskSidePanel(Step<?> task) {
        if (task != null) {
            updateNameAndDescription(task);
            addSettingsConfiguration(task);
        } else {
            updateNameAndDescription(selectedFlow);
            addSettingsConfiguration(selectedFlow);
        }

    }

    private void updateNameAndDescription(Step<?> task) {
        if (task.getClass().isAnnotationPresent(StepDescription.class)) {
            StepDescription taskDescription = task.getClass().getAnnotation(StepDescription.class);
            txtSelectedTaskDescription.setVisible(true);
            txtSelectedTaskDescription.setValue(taskDescription.value());
        } else {
            txtSelectedTaskDescription.setVisible(false);
            txtSelectedTaskDescription.setValue("");
        }
        if (task.getClass().isAnnotationPresent(StepName.class)) {
            StepName taskName = task.getClass().getAnnotation(StepName.class);
            spanSelectedTaskName.setText(taskName.value());
        } else {
            spanSelectedTaskName.setText(ViewUtils.splitCamelCase(task.getClass().getSimpleName()));
        }
    }

    private StepStatus getFlowStatus(FlowRunIdentifier identifier) {
        if (selectedFlow == null || identifier == null) {
            return StepStatus.NOT_STARTED;
        }
        if (selectedFlowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.RUNNING) ||
                selectedFlowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.READY)) {
            return StepStatus.RUNNING;
        }
        if (selectedFlowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.PENDING_REWIND) ||
                selectedFlowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.REWINDING)) {
            return StepStatus.REWINDING;
        }
        if (selectedFlowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.FAILED)) {
            return StepStatus.FAILED;
        }
        if (selectedFlowSteps.stream().anyMatch(t -> t.getStatus(identifier) == StepStatus.SUCCESS ||
                t.getStatus(identifier) == StepStatus.REWIND_SUCCESS || t.getStatus(identifier) == StepStatus.REWIND_FAILED)) {
            return StepStatus.SUCCESS;
        }
        return StepStatus.NOT_STARTED;

    }

    private void addSettingsConfiguration(Step<?> task) {
        pnlConfigureTaskSettings.removeAll();
        if (task == null) {
            return;
        }
        if (task instanceof ConfigurationSettings) {
            VerticalLayout panel = buildPropertiesUI(task, () -> settingsManager.settingUpdated((ConfigurationSettings) task));
            pnlConfigureTaskSettings.add(panel);
        }
    }

    private VerticalLayout buildPropertiesUI(Object task, Runnable runAfterSet) {
        List<Field> allFields = collectConfigurableFields(task);
        VerticalLayout panel = new VerticalLayout();
        panel.setPadding(false);
        panel.setMargin(false);
        panel.setSpacing(false);
        for (Field field : allFields) {
            UIConfigurable uiConfigurable = field.getAnnotation(UIConfigurable.class);
            if (field.getType().equals(boolean.class) || field.getType().equals(Boolean.class)) {
                Checkbox checkbox = new Checkbox(uiConfigurable.value());
                try {
                    field.setAccessible(true);
                    Boolean value = (Boolean) field.get(task);
                    checkbox.setValue(value != null && value);
                } catch (IllegalAccessException illegalAccessException) {
                }

                checkbox.addValueChangeListener(e -> {
                    try {
                        field.setAccessible(true);
                        field.set(task, e.getValue());
                    } catch (IllegalAccessException illegalAccessException) {
                    }
                    runAfterSet.run();
                });

                panel.add(checkbox);

            } else if (field.getType().equals(String.class)) {
                TextField textField = new TextField(uiConfigurable.value());
                try {
                    field.setAccessible(true);
                    String value = (String) field.get(task);
                    textField.setValue(value == null ? "" : value);
                } catch (IllegalAccessException illegalAccessException) {
                }

                textField.addValueChangeListener(e -> {
                    try {
                        field.setAccessible(true);
                        field.set(task, e.getValue());
                    } catch (IllegalAccessException illegalAccessException) {
                    }
                    runAfterSet.run();
                });
                textField.setWidthFull();
                panel.add(textField);
            } else if (field.getType().equals(int.class) || field.getType().equals(Integer.class)) {
                TextField textField = new TextField(uiConfigurable.value());
                try {
                    field.setAccessible(true);
                    Integer value = (Integer) field.get(task);
                    textField.setValue(value == null ? "" : value.toString());
                } catch (IllegalAccessException illegalAccessException) {
                }

                textField.addValueChangeListener(e -> {
                    try {
                        field.setAccessible(true);
                        field.set(task, Integer.parseInt(e.getValue()));
                    } catch (IllegalAccessException illegalAccessException) {
                    }
                    runAfterSet.run();
                });
                textField.setWidthFull();
                panel.add(textField);
            } else if (field.getType().equals(long.class) || field.getType().equals(Long.class)) {
                TextField textField = new TextField(uiConfigurable.value());
                try {
                    field.setAccessible(true);
                    Long value = (Long) field.get(task);
                    textField.setValue(value == null ? "" : value.toString());
                } catch (IllegalAccessException illegalAccessException) {
                }

                textField.addValueChangeListener(e -> {
                    try {
                        field.setAccessible(true);
                        field.set(task, Long.parseLong(e.getValue()));
                    } catch (IllegalAccessException illegalAccessException) {
                    }
                    runAfterSet.run();
                });
                textField.setWidthFull();
                panel.add(textField);
            }

        }
        return panel;
    }

    private List<Field> collectConfigurableFields(Object task) {
        List<Field> allFields = new ArrayList<>();
        Class<?> clazz = task.getClass();
        while (clazz != null) {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(UIConfigurable.class)) {
                    allFields.add(field);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return allFields;
    }



    @Override
    public void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        stepManager.addListener(this);
        appender.addLogEventListener(logsView);
    }

    @Override
    public void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        stepManager.removeListener(this);
        appender.removeLogEventListener(logsView);
        updateIRunIdentifierDebouncer.shutdown();
        updateTasksDebouncer.shutdown();
    }

    @Override
    public void stepChanged(Step<?> task, FlowRunIdentifier identifier) {

        Step<?> rootTask = stepManager.getFirstStep(task);
//            if (rootTask.getStatus(identifier) == TaskStatus.FAILED) {
//                dataProvider.refreshItem(rootTask);
//            }

        updateTasksDebouncer.debounce(rootTask.getClass().getSimpleName(), () -> {
            getUI().ifPresent(ui2 -> ui2.access(() -> {
                dataProvider.refreshAll();
            }));
        });

        if (rootTask == selectedFlow) {
            updateIRunIdentifierDebouncer.debounce("", () -> {
                getUI().ifPresent(ui2 -> ui2.access(() -> {
                    identifierDataProvider.refreshItem(identifier);
                }));
            });
        }
        if (identifier == selectedIdentifier && identifier != null) {
            getUI().ifPresent(ui -> ui.access(() -> {
                diagramView.updateTask(task);
                btnRewind.setVisible(identifier.isRewindArmed());
            }));
        }

    }


    @Override
    public void runAdded(Step<?> rootTask, FlowRunIdentifier identifier, boolean userInitiated) {
        getUI().ifPresent(ui -> ui.access(() -> {
            if (selectedFlow == rootTask) {
                identifiers.add(0, identifier);
                identifierDataProvider.refreshAll();
                dataProvider.refreshItem(rootTask);
                if (userInitiated) {
                    gridIdentifiers.select(identifier);
                }
            }
        }));
    }

    @Override
    public void runRemoved(Step<?> firstStep, FlowRunIdentifier identifier) {
        getUI().ifPresent(ui -> ui.access(() -> {
            if (selectedFlow == firstStep) {
                identifiers.remove(identifier);
                identifierDataProvider.refreshAll();
                dataProvider.refreshItem(firstStep);
            }
        }));
    }

    public void taskTreeSelectionChanged(Step<?> task, FlowRunIdentifier identifier) {

    }

}
