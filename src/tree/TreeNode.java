package tree;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class TreeNode implements Serializable {
	private static final long serialVersionUID = -5799914737841118887L;
	public static final String DELIM = "|#|";
	public static final int NODE_NOT_CHANGED = 0;
	public static final int NODE_INSERTED = 1;
	public static final int NODE_DELETED = 2;

	private int id;
	private transient ASTNode astNode;
	private String label;
	private transient String hashString;
	private int depth;
	private TreeVector vector;
	private TreeNode matched;
	private TreeNode parent;
	public List<TreeNode> children;
	private int changeType;
	private int lineNumber;
	private int offset;
	private transient Candidates candidates;

	public TreeNode(){
		this(-1, "root", null);
	}

	public TreeNode(int id, String label, ASTNode node){
		super();
		this.id = id;
		this.astNode = node;
		this.label = label;
		this.hashString = "";
		this.depth = -1;
		this.vector = new TreeVector(label);
		this.matched = null;
		this.parent = null;
		this.children = new ArrayList<TreeNode>();
		this.lineNumber = computeLineNumber();
		this.offset = 0;
		this.candidates = new Candidates();
	}

	public boolean isLeaf(){
		return children.size() == 0;
	}

	public boolean match(TreeNode node) {
		return this.getHash() == node.getHash();
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getHashString() {
		return hashString;
	}

	public void setHashString(String hashString) {
		this.hashString = hashString;
	}

	public TreeNode getParent() {
		return parent;
	}

	public void setParent(TreeNode parent) {
		this.parent = parent;
	}

	public int getDepth() {
		return depth;
	}

	public void setDepth(int depth) {
		this.depth = depth;
	}

	public int getType(){
		return astNode != null ? astNode.getNodeType() : -1;
	}

	public int getStartPosition(){
		return astNode.getStartPosition();
	}

	public int getLength(){
		return astNode.getLength();
	}

	public int getEndPosition(){
		return astNode.getStartPosition() + astNode.getLength();
	}

	public ASTNode getASTNode(){
		return astNode;
	}

	public int computeLineNumber(){
		if(astNode != null && astNode.getRoot() instanceof CompilationUnit){
			return ((CompilationUnit)astNode.getRoot()).getLineNumber(astNode.getStartPosition());
		}else{
			return -1;
		}
	}

	public int getHash(){
		return hashString.hashCode();
	}

	public boolean isMatched() {
		return matched != null;
	}

	public void setMatched(TreeNode matched){
		if(this.matched != null){
			System.err.println("Already matched to "+this.matched+", but tried to match again with "+matched);
			StackTraceElement[] trace = Thread.currentThread().getStackTrace();
			for (StackTraceElement traceElement : trace)
				System.err.println("\tat " + traceElement);
			System.exit(1);
		}
		this.matched = matched;
	}

	public TreeNode getMatched(){
		return matched;
	}

	public int indexInParent() {
		if(parent == null)
			return -1;
		return parent.children.indexOf(this);
	}

	public TreeVector getVector(){
		return vector;
	}

	public double similarity(TreeNode node){
		return this.vector.similarity(node.getVector());
	}

	public void addChild(TreeNode child){
		children.add(child);
		child.setParent(this);
	}

	public List<TreeNode> getUnmatchedChildren() {
		List<TreeNode> unmatched = new ArrayList<TreeNode>();
		for(TreeNode child : children){
			if(!child.isMatched())
				unmatched.add(child);
		}
		return unmatched;
	}

	@Override
	public String toString() {
		return label + "(" + lineNumber + ")";
	}

	public String toTreeString(){
		return toTreeString("");
	}

	public String toTreeString(String indent) {
		StringBuffer sb = new StringBuffer();
		sb.append(indent + this.toString());
		indent += "  ";
		for(TreeNode child : children){
			sb.append("\n");
			sb.append(child.toTreeString(indent));
		}
		return sb.toString();
	}

	public int getChangeType() {
		return changeType;
	}

	public void setChangeType(int changeType) {
		this.changeType = changeType;
	}

	public int getLineNumber(){
		return lineNumber;
	}

	public boolean isInserted(){
		return changeType == NODE_INSERTED;
	}

	public boolean isDeleted(){
		return changeType == NODE_DELETED;
	}

	@Override
	public boolean equals(Object obj) {
		if(obj instanceof TreeNode){
			return this.id == ((TreeNode)obj).getId();
		}
		return false;
	}

	public int getOffset() {
		return offset;
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}

	public void addCandidate(TreeNode c, double similarity){
		this.candidates.addCandidate(c, similarity);
	}

	public TreeNode getBestMatch(){
		Candidate best = this.candidates.peek();
		return best == null ? null : best.node;
	}

	public TreeNode nextMatch(){
		this.candidates.remove();
		Candidate next = this.candidates.peek();
		return next == null ? null : next.node;
	}

	public TreeNode getLeft(){
		if(this.parent == null){
			return null;
		}else{
			int index = indexInParent();
			return index > 0 ? this.parent.children.get(index - 1) : null;
		}
	}

	public TreeNode getRight(){
		if(this.parent == null){
			return null;
		}else{
			int index = indexInParent();
			return index < this.parent.children.size() - 1 ? this.parent.children.get(index + 1) : null;
		}
	}
}
