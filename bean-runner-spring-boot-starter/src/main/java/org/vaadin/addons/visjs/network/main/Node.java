package org.vaadin.addons.visjs.network.main;

import org.vaadin.addons.visjs.network.options.nodes.Nodes;

public class Node extends Nodes {

  private String id;

  // @JsonIgnore
  // private transient List<Edge> edgeList = new ArrayList<>();;
  // @JsonIgnore
  // private transient Map<String, Edge> edgeMap = new HashMap<>();;

  public Node() {};

  public Node(final String id) {
    this.id = id;
  }

  public Node(final String id, Builder builder) {
    super(builder);
    this.id = id;
  }

  public Node(String id, String label) {
    this.id = id;
    this.setLabel(label);
  }

  public Node(String id, String label, final Builder builder) {
    super(builder);
    this.id = id;
    this.setLabel(label);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  // FIXME sync with JS-Component
  // public List<Edge> getConnectedEdges() {
  // return edgeList;
  // }
  //
  // public void setConnectedEdges(List<Edge> edgeList) {
  // this.edgeList = edgeList;
  // }
  //
  // public void addEdgeToList(Edge edge) {
  // edgeList.add(edge);
  // }
  //
  // public void removeEdgeFromList(Edge edge) {
  // edgeList.remove(edge);
  // }
  //
  // public Map<String, Edge> getEdgeMap() {
  // return edgeMap;
  // }
  //
  // public void setEdgeMap(Map<String, Edge> edgeMap) {
  // this.edgeMap = edgeMap;
  // }

}
