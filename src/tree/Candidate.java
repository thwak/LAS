package tree;

public class Candidate {

	public TreeNode node;
	public double similarity;

	public Candidate(TreeNode node, double similarity){
		this.node = node;
		this.similarity = similarity;
	}
}
