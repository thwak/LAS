package script.model;

import tree.TreeNode;

public class Insert extends EditOp {

	public Insert(TreeNode node) {
		super(node, node.getParent(), node.indexInParent());
		node.setChangeType(TreeNode.NODE_INSERTED);
	}

	@Override
	public String getType(){
		return "insert";
	}

}
