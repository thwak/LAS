package script.model;

import tree.TreeNode;

public class Update extends EditOp {

	public Update(TreeNode bNode, TreeNode aNode) {
		super(bNode, aNode, -1);
	}

	@Override
	public String getType(){
		return "update";
	}

}
