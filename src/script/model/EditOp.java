package script.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import tree.TreeNode;

public class EditOp implements Serializable {

	private static final long serialVersionUID = 4494280236401405865L;
	public static final String SYM_OPEN = "(";
	public static final String SYM_CLOSE = ")";
	public static final String SYM_DELIM = ",";

	protected TreeNode node;
	protected TreeNode location;
	protected int position;
	protected List<EditOp> children;
	protected int size;

	public EditOp(TreeNode node, TreeNode location, int position){
		this.node = node;
		this.location = location;
		this.position = position;
		this.children = new ArrayList<>();
		this.size = 0;
	}

	public String getType() {
		return "edit";
	}

	public TreeNode getNode() {
		return this.node;
	}

	public TreeNode getLocation() {
		return this.location;
	}

	public int getPosition() {
		return this.position;
	}

	public String toOpString() {
		return toOpString("");
	}

	public int size(){
		if(size == 0){
			size++;
			for(EditOp child : children){
				size += child.size();
			}
		}
		return size;
	}

	@Override
	public String toString(){
		return getType() + "\t" + node.getLabel() + EditOp.SYM_OPEN + node.getLineNumber() + EditOp.SYM_CLOSE + EditOp.SYM_DELIM
				+ location.getLabel() + EditOp.SYM_OPEN + location.getLineNumber() + EditOp.SYM_CLOSE + EditOp.SYM_DELIM + position;
	}

	public String toOpString(String indent){
		StringBuffer sb = new StringBuffer();
		sb.append(indent + getType() + "\t" + node.getLabel() + EditOp.SYM_OPEN + node.getLineNumber() + EditOp.SYM_CLOSE + EditOp.SYM_DELIM
				+ location.getLabel() + EditOp.SYM_OPEN + location.getLineNumber() + EditOp.SYM_CLOSE + EditOp.SYM_DELIM + position);
		indent += "\t";
		for(EditOp child : children){
			sb.append("\n");
			sb.append(child.toOpString(indent));
		}
		return sb.toString();
	}

	public void addEditOp(EditOp op){
		children.add(op);
	}

	public List<EditOp> getSubtreeEdit(){
		List<EditOp> editOps = new ArrayList<>();
		editOps.add(this);
		for(EditOp child : children){
			editOps.addAll(child.getSubtreeEdit());
		}
		return editOps;
	}

	public Iterator<EditOp> childEdits() {
		return children.iterator();
	}

	public boolean attach(EditOp op) {
		TreeNode n = op.getNode();
		boolean attached = false;
		if(this.node == n.getParent()) {
			for(int i=0; i<children.size(); i++) {
				TreeNode curr = children.get(i).getNode();
				if(n.getId() < curr.getId()) {
					children.add(i, op);
					attached = true;
					break;
				} else if(n.getId() == curr.getId()) {
					attached = true;
					break;
				}
			}
			if(!attached)
				children.add(op);
			return true;
		} else {
			for(EditOp child : children) {
				attached = attached || child.attach(op);
				if(attached)
					break;
			}
			return attached;
		}
	}
}
