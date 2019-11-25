package script.model;

import tree.TreeNode;

public class Replace extends EditOp {

	private static final long serialVersionUID = 2988734838963648585L;

	public Replace(TreeNode node, TreeNode replacement) {
		super(node, replacement, -1);
	}

	@Override
	public String getType(){
		return "replace";
	}

	@Override
	public String toString(){
		return getType() + "\t" + node.getLabel() + EditOp.SYM_OPEN + node.getLineNumber() + EditOp.SYM_CLOSE + " with "
				+ location.getLabel() + EditOp.SYM_OPEN + location.getLineNumber() + EditOp.SYM_CLOSE;
	}

	@Override
	public String toOpString(){
		return getType() + "\t" + node.getLabel() + EditOp.SYM_OPEN + node.getLineNumber() + EditOp.SYM_CLOSE + " with "
				+ location.getLabel() + EditOp.SYM_OPEN + location.getLineNumber() + EditOp.SYM_CLOSE;
	}
}
