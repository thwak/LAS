package script.model;

import tree.TreeNode;

public class Move extends EditOp {

	public Move(TreeNode node, TreeNode location, int position) {
		super(node, location, position);
	}

	@Override
	public String getType(){
		return "move";
	}

	@Override
	public String toString(){
		return getType() + "\t" + node.getLabel() + EditOp.SYM_OPEN + node.getLineNumber() + EditOp.SYM_CLOSE + " from "
				+ node.getParent().getLabel() + EditOp.SYM_OPEN + node.getParent().getLineNumber() + EditOp.SYM_CLOSE + EditOp.SYM_DELIM + node.indexInParent() + " to "
				+ location.getLabel() + EditOp.SYM_OPEN + location.getLineNumber() + EditOp.SYM_CLOSE + EditOp.SYM_DELIM + position;
	}

	@Override
	public String toOpString(){
		return getType() + "\t" + node.getLabel() + EditOp.SYM_OPEN + node.getLineNumber() + EditOp.SYM_CLOSE + " from "
				+ node.getParent().getLabel() + EditOp.SYM_OPEN + node.getParent().getLineNumber() + EditOp.SYM_CLOSE + EditOp.SYM_DELIM + node.indexInParent() + " to "
				+ location.getLabel() + EditOp.SYM_OPEN + location.getLineNumber() + EditOp.SYM_CLOSE + EditOp.SYM_DELIM + position;
	}
}