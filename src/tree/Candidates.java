package tree;

import java.util.Comparator;
import java.util.PriorityQueue;

public class Candidates {

	private PriorityQueue<Candidate> candidates;

	public Candidates(){
		candidates = new PriorityQueue<Candidate>(2, new Comparator<Candidate>() {
			@Override
			public int compare(Candidate c1, Candidate c2) {
				return -1*Double.compare(c1.similarity, c2.similarity);
			}
		});
	}

	public void addCandidate(Candidate c){
		candidates.add(c);
	}

	public void addCandidate(TreeNode node, double similarity){
		Candidate c = new Candidate(node, similarity);
		candidates.add(c);
	}

	public Candidate peek(){
		return candidates.peek();
	}

	public Candidate poll(){
		return candidates.poll();
	}

	public Candidate remove(){
		return candidates.remove();
	}
}
