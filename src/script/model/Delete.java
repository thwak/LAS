package script.model;

import tree.TreeNode;

public class Delete extends EditOp {

	public Delete(TreeNode node){
		super(node, node.getParent(), node.indexInParent());
	}

	@Override
	public String getType(){
		return "delete";
	}

}
