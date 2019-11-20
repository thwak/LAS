package script.model;

import tree.TreeNode;

public class Insert extends EditOp {

	private static final long serialVersionUID = 4078470799680398350L;

	public Insert(TreeNode node) {
		super(node, node.getParent(), node.indexInParent());
		node.setChangeType(TreeNode.NODE_INSERTED);
	}

	@Override
	public String getType(){
		return "insert";
	}

}
