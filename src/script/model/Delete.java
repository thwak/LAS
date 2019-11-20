package script.model;

import tree.TreeNode;

public class Delete extends EditOp {

	private static final long serialVersionUID = 5143389519368553567L;

	public Delete(TreeNode node){
		super(node, node.getParent(), node.indexInParent());
	}

	@Override
	public String getType(){
		return "delete";
	}

}
