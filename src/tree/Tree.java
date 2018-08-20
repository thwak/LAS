package tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class Tree {
	public static final String SYM_OPEN = "{";
	public static final String SYM_CLOSE = "}";

	private String name;
	private TreeNode root;
	private int size;
	private Map<Integer, List<TreeNode>> depthMap;
	private List<TreeNode> leaves;

	public Tree(String name){
		this(name, new TreeNode());
	}

	public Tree(String name, TreeNode root){
		this.name = name;
		this.root = root;
		this.size = 0;
		this.depthMap = new HashMap<Integer, List<TreeNode>>();
		this.leaves = new ArrayList<TreeNode>();
	}

	public List<TreeNode> dfs(){
		List<TreeNode> visitedNodes = new ArrayList<TreeNode>();
		for(TreeNode child : root.children){
			visitedNodes.addAll(dfs(child));
		}
		return visitedNodes;
	}

	public List<TreeNode> bfs(){
		List<TreeNode> visitedNodes = new ArrayList<TreeNode>();
		visitedNodes.addAll(root.children);
		for(TreeNode child : root.children){
			visitedNodes.addAll(bfs(child));
		}
		return visitedNodes;
	}

	public List<TreeNode> bfs(TreeNode node){
		List<TreeNode> visitedNodes = new ArrayList<TreeNode>();
		List<TreeNode> queue = new ArrayList<TreeNode>(node.children);
		int size = queue.size();
		while(queue.size() > 0){
			visitedNodes.addAll(queue);
			for(int i=0; i<size; i++){
				TreeNode front = queue.remove(0);
				queue.addAll(front.children);
			}
			size = queue.size();
		}
		return visitedNodes;
	}

	public List<TreeNode> dfs(TreeNode node){
		List<TreeNode> visitedNodes = new ArrayList<TreeNode>();
		visitedNodes.add(node);
		for(TreeNode child : node.children){
			visitedNodes.addAll(dfs(child));
		}
		return visitedNodes;
	}

	public void computeHashString(){
		for(TreeNode child : root.children){
			computeHashString(child);
		}
	}

	public String computeHashString(TreeNode node){
		StringBuffer sb = new StringBuffer();
		sb.append(Tree.SYM_OPEN);
		sb.append(node.getLabel());
		for(TreeNode child : node.children){
			sb.append(computeHashString(child));
		}
		sb.append(Tree.SYM_CLOSE);
		node.setHashString(sb.toString());
		return node.getHashString();
	}

	public void computeDepth(){
		List<TreeNode> nodes = bfs();
		for(TreeNode node : nodes){
			node.setDepth(node.getParent().getDepth()+1);
			if(!depthMap.containsKey(node.getDepth())){
				depthMap.put(node.getDepth(), new ArrayList<TreeNode>());
			}
			depthMap.get(node.getDepth()).add(node);
			if(node.isLeaf())
				leaves.add(node);
		}
	}

	public List<TreeNode> getUnmatchedLeaves(){
		List<TreeNode> unmatched = new ArrayList<TreeNode>();
		for(TreeNode leaf : leaves){
			if(!leaf.isMatched())
				unmatched.add(leaf);
		}
		return unmatched;
	}

	public List<TreeNode> getAdjacentNodes(TreeNode node, int dist){
		List<TreeNode> adjNodes = new ArrayList<TreeNode>();
		List<TreeNode> nodes = node.getParent() != null ? node.getParent().children : depthMap.get(node.getDepth());
		int index = nodes.indexOf(node);
		//Add nodes near the given node which belong to the same parent.
		for (int j = index + 1; j <= index + dist && j < nodes.size(); j++) {
			adjNodes.add(nodes.get(j));
		}
		for (int j = index - 1; j >= index - dist && j >= 0 && j < nodes.size(); j--) {
			adjNodes.add(nodes.get(j));
		}
		return adjNodes;
	}

	public List<TreeNode> getNearDepthNodes(List<TreeNode> nodes, int depth, int dist){
		List<TreeNode> nearDepthNodes = new ArrayList<TreeNode>();
		//Add nodes above/below the given nodes.
		List<TreeNode> adjParents = new ArrayList<TreeNode>();
		List<TreeNode> adjChildren = new ArrayList<TreeNode>();
		for (int i = 0; i < depth; i++) {
			adjChildren = getChildren(adjChildren);
			nearDepthNodes.addAll(adjChildren);
			adjParents = getParents(adjParents, dist);
			nearDepthNodes.addAll(adjParents);
		}
		return nearDepthNodes;
	}

	private List<TreeNode> getParents(List<TreeNode> nodes, int dist) {
		List<TreeNode> adjParents = new ArrayList<TreeNode>();
		TreeSet<TreeNode> parents = new TreeSet<TreeNode>(new Comparator<TreeNode>() {
			@Override
			public int compare(TreeNode o1, TreeNode o2) {
				return Integer.compare(o1.getId(), o2.getId());
			}
		});
		//First identify parents of given nodes.
		for(TreeNode node : nodes){
			if(node.getParent() != null)
				parents.add(node.getParent());
		}

		//Then get adjacent nodes of the parents.
		adjParents.addAll(parents);
		for(TreeNode parent : adjParents){
			parents.addAll(getAdjacentNodes(parent, dist));
		}
		adjParents.clear();
		adjParents.addAll(parents);

		return adjParents;
	}

	private List<TreeNode> getChildren(List<TreeNode> nodes) {
		List<TreeNode> adjChildren = new ArrayList<TreeNode>();
		for(TreeNode node : nodes){
			adjChildren.addAll(node.getUnmatchedChildren());
		}
		return adjChildren;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public TreeNode getRoot() {
		return root;
	}

	public void setRoot(TreeNode root) {
		this.root = root;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public List<TreeNode> getLeaves(){
		return this.leaves;
	}

	public int getHeight(){
		return depthMap.size();
	}

	public List<TreeNode> getNodesAtDepth(int depth){
		return depthMap.get(depth);
	}
}
