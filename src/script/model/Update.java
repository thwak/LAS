package script.model;

import tree.TreeNode;

public class Update extends EditOp {

	private static final long serialVersionUID = 2080389627094732543L;

	public Update(TreeNode bNode, TreeNode aNode) {
		super(bNode, aNode, -1);
	}

	@Override
	public String getType(){
		return "update";
	}

	@Override
	public String toString(){
		return getType() + "\t" + node.getLabel() + EditOp.SYM_OPEN + node.getLineNumber() + EditOp.SYM_CLOSE + " to "
				+ location.getLabel();
	}

	@Override
	public String toOpString(){
		return getType() + "\t" + node.getLabel() + EditOp.SYM_OPEN + node.getLineNumber() + EditOp.SYM_CLOSE + " to "
				+ location.getLabel();
	}
}
