package script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTNode;

import script.model.Delete;
import script.model.EditOp;
import script.model.EditScript;
import script.model.Insert;
import script.model.Move;
import script.model.Update;
import tree.Tree;
import tree.TreeNode;

public class ScriptGenerator {

	private static final double DIST_THRESHOLD = System.getProperty("las.dist.threshold") == null ? 0.5d : Double.parseDouble(System.getProperty("las.dist.threshold"));
	private static final int DEPTH_THRESHOLD = System.getProperty("las.depth.threshold") == null ? 3 : Integer.parseInt(System.getProperty("las.depth.threshold"));
	private static final double SIM_THRESHOLD = System.getProperty("las.sim.threshold") == null ? 0.65d : Double.parseDouble(System.getProperty("las.sim.threshold"));
	private static final boolean ENABLE_EXACT_MATCH =  System.getProperty("las.enable.exact") == null ? true : Boolean.parseBoolean(System.getProperty("las.enable.exact"));

	public static int exactMatch = 0;
	public static int similarMatch = 0;
	public static int followupMatch = 0;
	public static int leafMatch = 0;
	public static int exactMatchCount = 0;

	public static EditScript generateScript(Tree before, Tree after){
		exactMatch = 0;
		similarMatch = 0;
		followupMatch = 0;
		leafMatch = 0;
		exactMatchCount = 0;
		match(before, after);
		EditScript script = generateEditOps(before, after);
		script.updateMatchCount(exactMatch, similarMatch, followupMatch, leafMatch, exactMatchCount);

		return script;
	}

	private static EditScript generateEditOps(
			Tree before, Tree after) {
		EditScript script = new EditScript();
		//Generate delete first.
		Stack<EditOp> opStack = new Stack<EditOp>();
		for(TreeNode node : before.getRoot().children){
			script.addEditOps(generateDelete(node, opStack));
		}

		//Then generate insert, move, update.
		opStack.clear();
		for(TreeNode node : after.getRoot().children){
			script.addEditOps(generateInsertMoveUpdate(node, opStack));
		}

		//Finally, generate move operations for ordering changes.
		script.addEditOps(generateOrderingChange(before.getRoot()));

		return script;
	}

	private static List<Move> generateOrderingChange(TreeNode node) {
		List<Move> editOps = new ArrayList<>();
		//node must be from the old tree.
		if(node.isMatched()){
			List<TreeNode> oldNodes = node.children;
			List<TreeNode> newNodes = node.getMatched().children;
			for(TreeNode n : findNonLCSNodes(oldNodes, newNodes)){
				Move move = new Move(n, n.getMatched().getParent(), n.getMatched().indexInParent());
				editOps.add(move);
			}
		}
		for(TreeNode child : node.children){
			editOps.addAll(generateOrderingChange(child));
		}
		return editOps;
	}

	private static List<TreeNode> findNonLCSNodes(List<TreeNode> oldNodes, List<TreeNode> newNodes) {
		List<TreeNode> nonLCSNodes = new ArrayList<TreeNode>();
		int m = oldNodes.size();
		int n = newNodes.size();
		if(m == 0 || n == 0){
			return nonLCSNodes;
		}
		int[][] len = new int[m+1][n+1];
		TreeNode oldNode;
		TreeNode newNode;
		for(int i=m-1; i>=0; i--) {
			for(int j=n-1; j>=0; j--) {
				oldNode = oldNodes.get(i);
				newNode = newNodes.get(j);
				if(oldNode.isMatched() &&
						oldNode.getMatched() == newNode){
					len[i][j] = len[i+1][j+1] + 1;
				}else{
					len[i][j] = Math.max(len[i+1][j], len[i][j+1]);
				}
			}
		}
		int i = 0, j = 0;
		while(i<m && j<n) {
			oldNode = oldNodes.get(i);
			newNode = newNodes.get(j);
			if(oldNode.isMatched() &&
					oldNode.getMatched() == newNode) {
				i++;
				j++;
			}else if(len[i+1][j] >= len[i][j+1]
					|| !oldNode.isMatched()){
				if(!oldNode.isDeleted())
					nonLCSNodes.add(oldNode);
				i++;
			}else{
				j++;
			}
		}
		return nonLCSNodes;
	}

	/**
	 * Generate insert, move operations for inserted, moved subtrees.
	 * Also generate update operations for updated nodes.
	 *
	 * @param node the root of subtree.
	 * @param opStack a stack of insert operations currently processing.
	 * @return a list of operations.
	 */
	private static List<? extends EditOp> generateInsertMoveUpdate(TreeNode node,
			Stack<EditOp> opStack) {
		List<EditOp> editOps = new ArrayList<EditOp>();
		boolean isPushed = false;
		if(node.isMatched()){
			TreeNode parent = node.getParent();
			TreeNode parentOfMatched = node.getMatched().getParent();
			if(parent != null
					&& parent.getMatched() != parentOfMatched){
				node.setChangeType(TreeNode.NODE_INSERTED);
				node.getMatched().setChangeType(TreeNode.NODE_DELETED);
				Move move = new Move(node.getMatched(), parent, node.indexInParent());
				//A subtree can be moved to an inserted node, so need to check op type.
				if(!opStack.isEmpty() && opStack.peek() instanceof Move){
					opStack.peek().addEditOp(move);
				}else{
					editOps.add(move);
				}
				opStack.push(move);
				isPushed = true;
			}
			if(!node.getLabel().equals(node.getMatched().getLabel())){
				editOps.add(new Update(node.getMatched(), node));
			}
		}else{
			node.setChangeType(TreeNode.NODE_INSERTED);
			Insert insert = new Insert(node);
			if(!opStack.isEmpty() && opStack.peek() instanceof Insert){
				opStack.peek().addEditOp(insert);
			}else{
				editOps.add(insert);
			}
			opStack.push(insert);
			isPushed = true;
		}
		for(TreeNode child : node.children){
			editOps.addAll(generateInsertMoveUpdate(child, opStack));
		}
		if(isPushed)
			opStack.pop();
		return editOps;
	}

	/**
	 * Generate delete operations for each deleted subtree.
	 *
	 * @param node the root of subtree.
	 * @param opStack a stack of delete operations currently processing.
	 * @return a list of delete operations.
	 */
	private static List<? extends EditOp> generateDelete(TreeNode node, Stack<EditOp> opStack) {
		List<EditOp> delOps = new ArrayList<EditOp>();
		if(!node.isMatched()){
			node.setChangeType(TreeNode.NODE_DELETED);
			Delete delete = new Delete(node);
			//If opStack is empty, it is the root of a deleted subtree.
			if(opStack.empty()){
				delOps.add(delete);
			}else{
				opStack.peek().addEditOp(delete);
			}
			opStack.push(delete);
		}
		for(TreeNode child : node.children){
			delOps.addAll(generateDelete(child, opStack));
		}
		if(!node.isMatched())
			opStack.pop();
		return delOps;
	}

	/**
	 * Match nodes between two AST versions.
	 *
	 * @param before an AST before a change.
	 * @param after an AST after a change.
	 */
	private static void match(Tree before, Tree after) {
		before.getRoot().setMatched(after.getRoot());
		after.getRoot().setMatched(before.getRoot());
		if(ENABLE_EXACT_MATCH)
			exactMatch(before.getRoot().children, after.getRoot().children, before, after);
		similarMatch(before, after);
		updateFollowUpMatch(before, after);
		matchLeaves(before, after);
	}

	private static void updateFollowUpMatch(Tree before, Tree after) {
		List<TreeNode> bfs = before.bfs();
		for(TreeNode node : bfs){
			if(!node.isMatched() && !node.isLeaf()){
				if(node.getType() == ASTNode.BLOCK){
					if(node.getParent() != null && node.getParent().isMatched()){
						for(TreeNode child : node.getParent().getMatched().children){
							if(!child.isMatched() && child.getType() == ASTNode.BLOCK){
								node.setMatched(child);
								child.setMatched(node);
								followupMatch += 2;
								break;
							}
						}
					}
					continue;
				}
				findFollowUpMatch(node);
			}
		}
		bfs = after.bfs();
		for(TreeNode node : bfs){
			if(!node.isMatched() && !node.isLeaf()){
				if(node.getType() == ASTNode.BLOCK){
					if(node.getParent() != null && node.getParent().isMatched()){
						for(TreeNode child : node.getParent().getMatched().children){
							if(!child.isMatched() && child.getType() == ASTNode.BLOCK){
								node.setMatched(child);
								child.setMatched(node);
								followupMatch += 2;
								break;
							}
						}
					}
					continue;
				}
				findFollowUpMatch(node);
			}
		}
	}

	private static void findFollowUpMatch(TreeNode node) {
		TreeNode match = null;
		for(TreeNode child : node.children){
			//If a child is an unmatched block, need to check it first.
			if(!child.isMatched() && child.getType() == ASTNode.BLOCK){
				findFollowUpMatch(child);
			}
			if(child.isMatched()){
				TreeNode candidate = child.getMatched().getParent();
				if (!candidate.isMatched()
						&& candidate.getType() == node.getType()) {
					TreeNode nodeParent = node.getParent();
					TreeNode candidateParent = candidate.getParent();
					//If parents are also matched, it is the match.
					if(nodeParent != null && !nodeParent.isMatched()
							&& nodeParent.getMatched() == candidateParent){
						node.setMatched(candidate);
						candidate.setMatched(node);
						followupMatch += 2;
						return;
					}
					if(match == null){
						match = candidate;
					}else if(match != candidate){
						return;
					}
				}
			}
		}
		//If a match was not found from children matches, try left/right node.
		if(match == null){
			match = findNeighborMatch(node);
		}

		if(match != null){
			node.setMatched(match);
			match.setMatched(node);
			followupMatch += 2;
		}
	}

	private static TreeNode findNeighborMatch(TreeNode node) {
		TreeNode match = null;
		TreeNode nodeParent = node.getParent();
		if (nodeParent != null
				&& nodeParent.isMatched()) {
			TreeNode left = node.getLeft();
			TreeNode right = node.getRight();
			TreeNode parentMatch = nodeParent.getMatched();
			//Need to handle cases when left or right is not matched.
			if(left != null && left.isMatched()){
				TreeNode leftMatch = left.getMatched();
				if(parentMatch.equals(leftMatch.getParent())){
					TreeNode candidate = leftMatch.getRight();
					if(candidate != null
							&& !candidate.isMatched()
							&& candidate.getType() == node.getType()
							&& leafSimilarity(node, candidate) >= SIM_THRESHOLD){
						match = candidate;
					}
				}
			}else if(right != null && right.isMatched()){
				TreeNode rightMatch = right.getMatched();
				if(parentMatch.equals(rightMatch.getParent())){
					TreeNode candidate = rightMatch.getLeft();
					if(candidate != null
							&& !candidate.isMatched()
							&& candidate.getType() == node.getType()
							&& leafSimilarity(node, candidate) >= SIM_THRESHOLD){
						match = candidate;
					}
				}
			}
		}
		return match;
	}

	private static double leafSimilarity(TreeNode node, TreeNode candidate) {
		Map<String, Integer> leafCount = new HashMap<String, Integer>();
		double numOfLeaves = 0.0d;
		int notMatchedLeaves = 0;
		for(TreeNode child : node.children){
			if(child.isLeaf()){
				String label = child.getLabel();
				if(!leafCount.containsKey(label)){
					leafCount.put(label, 0);
				}
				leafCount.put(label, leafCount.get(label)+1);
				numOfLeaves++;
			}
		}
		for(TreeNode child : candidate.children){
			if(child.isLeaf()){
				String label = child.getLabel();
				if(leafCount.containsKey(label)){
					leafCount.put(label, leafCount.get(label)-1);
				}else{
					notMatchedLeaves++;
				}
				numOfLeaves++;
			}
		}
		for(String key : leafCount.keySet()){
			notMatchedLeaves += leafCount.get(key);
		}

		return 1.0d - notMatchedLeaves/numOfLeaves;
	}

	/**
	 * Match nodes using similarity metric.
	 *
	 * @param before an AST before a change.
	 * @param after an AST after a change.
	 */
	private static void similarMatch(Tree before, Tree after) {
		similarMatch(before.getRoot().children, after.getRoot().children);
	}

	private static void similarMatch(List<TreeNode> xNodes, List<TreeNode> yNodes) {
		//Compute and update candidates with similarity higher than threshold.
		updateCandidates(xNodes, yNodes);
		updateCandidates(yNodes, xNodes);

		//Check mutually matched nodes.
		for(TreeNode node : xNodes){
			TreeNode match = node.getBestMatch();
			if(match != null && !match.isMatched()
					&& node.equals(match.getBestMatch())){
				node.setMatched(match);
				match.setMatched(node);
				similarMatch += 2;
				similarMatch(node.children, match.children);
			}
		}

		for(TreeNode node : yNodes){
			TreeNode match = node.getBestMatch();
			if(match != null && !match.isMatched()
					&& node.equals(match.getBestMatch())){
				node.setMatched(match);
				match.setMatched(node);
				similarMatch += 2;
				similarMatch(node.children, match.children);
			}
		}

		//Match children of unmatched nodes;
		List<TreeNode> xChildren = new ArrayList<TreeNode>();
		List<TreeNode> yChildren = new ArrayList<TreeNode>();
		for(TreeNode node : xNodes){
			if(!node.isMatched())
				xChildren.addAll(node.children);
		}
		for(TreeNode node : yNodes){
			if(!node.isMatched())
				yChildren.addAll(node.children);
		}

		if(xChildren.size() > 0 && yChildren.size() > 0){
			similarMatch(xChildren, yChildren);
		}else if(xChildren.size() > 0 && yChildren.size() == 0){
			similarMatch(xChildren, yNodes);
		}else if(yChildren.size() > 0 && xChildren.size() == 0){
			similarMatch(yChildren, xNodes);
		}
	}

	private static void updateCandidates(List<TreeNode> xNodes, List<TreeNode> yNodes) {
		TreeNode x = null;
		TreeNode y = null;
		for(int i=0; i<xNodes.size(); i++){
			x = xNodes.get(i);
			y = i < yNodes.size() ? yNodes.get(i) : y;
			if(!x.isLeaf() && !x.isMatched()){
				List<TreeNode> candidates = findCandidates(x, y);
				for (TreeNode c : candidates) {
					double similarity = x.similarity(c);
					if(similarity == 1.0d){
						x.addCandidate(c, 1.0d);
						break;
					}else if(similarity >= SIM_THRESHOLD){
						x.addCandidate(c, similarity);
					}
				}
			}
		}
	}

	/**
	 * Match leaves of given ASTs.
	 *
	 * @param before an AST before a change.
	 * @param after an AST after a change.
	 */
	private static void matchLeaves(Tree before, Tree after) {
		List<TreeNode> bfs = before.bfs();
		for(TreeNode node : bfs){
			if (node.isMatched() && !node.isLeaf()) {
				List<TreeNode> unmatchedLeaves = new ArrayList<TreeNode>();
				//Get unmatched leaves.
				for (TreeNode child : node.getUnmatchedChildren()) {
					if (child.isLeaf()) {
						unmatchedLeaves.add(child);
					}
				}
				//Find exact match first.
				List<TreeNode> candidates = node.getMatched().getUnmatchedChildren();
				List<TreeNode> matched = new ArrayList<TreeNode>();
				for(TreeNode leaf : unmatchedLeaves){
					for (TreeNode candidate : candidates) {
						if (!candidate.isMatched() &&
								leaf.getLabel().equals(candidate.getLabel())) {
							leaf.setMatched(candidate);
							candidate.setMatched(leaf);
							leafMatch += 2;
							matched.add(leaf);
							break;
						}
					}
				}
				//If there exist any unmatched leaves, match them based on the position.
				unmatchedLeaves.removeAll(matched);
				candidates = node.getMatched().getUnmatchedChildren();
				for(TreeNode leaf : unmatchedLeaves){
					for (TreeNode candidate : candidates) {
						if (!candidate.isMatched() &&
								leaf.getType() == candidate.getType()) {
							leaf.setMatched(candidate);
							candidate.setMatched(leaf);
							leafMatch += 2;
							matched.add(leaf);
							break;
						}
					}
				}
			}
		}
	}

	/**
	 * Find exact matches between given sets of nodes using hash.
	 *
	 * @param xNodes nodes belong to an AST <code>xTree</code> for matching.
	 * @param yNodes nodes belong to an AST <code>yTree</code> for matching.
	 * @param xTree
	 * @param yTree
	 */
	private static void exactMatch(List<TreeNode> xNodes, List<TreeNode> yNodes, Tree xTree, Tree yTree) {
		//if xNodes have less nodes, swap.
		if(xNodes.size() < yNodes.size()){
			List<TreeNode> tempNodes = xNodes;
			xNodes = yNodes;
			yNodes = tempNodes;
			Tree temp = xTree;
			xTree = yTree;
			yTree = temp;
		}
		for(int i=0; i<yNodes.size(); i++){
			TreeNode x = xNodes.get(i);
			TreeNode y = yNodes.get(i);
			if(!x.isLeaf() && !x.isMatched())
				exactMatch(x, y);
		}
		//Match remaining xNodes.
		TreeNode lastY = yNodes.get(yNodes.size()-1);
		for(int i=yNodes.size(); i<xNodes.size(); i++){
			TreeNode x = xNodes.get(i);
			if(!x.isLeaf() && !x.isMatched())
				exactMatch(x, lastY);
		}

		//Match children of unmatched nodes;
		List<TreeNode> xChildren = new ArrayList<TreeNode>();
		List<TreeNode> yChildren = new ArrayList<TreeNode>();
		for(TreeNode x : xNodes){
			if(!x.isMatched()){
				xChildren.addAll(x.children);
			}
		}
		for(TreeNode y : yNodes){
			if(!y.isMatched()){
				yChildren.addAll(y.children);
			}
		}
		if(xChildren.size() > 0 && yChildren.size() > 0){
			exactMatch(xChildren, yChildren, xTree, yTree);
		}else if(xChildren.size() > 0 && yChildren.size() == 0){
			exactMatch(xChildren, yNodes, xTree, yTree);
		}else if(xChildren.size() == 0 && yChildren.size() > 0){
			exactMatch(yChildren, xNodes, yTree, xTree);
		}
	}

	/**
	 * Find an exact match for node <code>x</code> from nodes adjacent to node <code>y</code>
	 * @param x a query node.
	 * @param y the starting node for matching.
	 */
	private static void exactMatch(TreeNode x, TreeNode y) {
		List<TreeNode> candidates = findCandidates(x, y);
		for(TreeNode candidate : candidates){
			if (!candidate.isMatched() && x.match(candidate)) {
				exactMatchCount+=2;
				exactMatch+=2;
				updateMatch(x, candidate);
				exactMatch += updateChildMatch(x, candidate);
				break;
			}
		}
	}

	/**
	 * Find match candidates for a given node <code>x</code> from adjacent nodes of node <code>y</code>.
	 * This method should be only used for finding exact matches.
	 *
	 * @param x a query node.
	 * @param y a starting node for candidate search.
	 * @return a list of candidate nodes.
	 */
	private static List<TreeNode> findCandidates(TreeNode x, TreeNode y){
		List<TreeNode> candidates = new ArrayList<TreeNode>();
		//y and adjacent nodes in the same depth.
		List<TreeNode> siblings = getSiblings(y);
		siblings.add(0, y);
		for(TreeNode node : siblings){
			if(node.getType() == x.getType())
				candidates.add(node);
		}
		//Same depth nodes (child nodes of parent's siblings)
		List<TreeNode> parentSiblings = getSiblings(y.getParent());
		for(TreeNode node : parentSiblings){
			for(TreeNode child : node.children){
				if(child.getType() == x.getType())
					candidates.add(child);
			}
		}
		//y and sibling's descendants,
		int depth = 1;
		while (depth <= DEPTH_THRESHOLD) {
			List<TreeNode> descendants = new ArrayList<TreeNode>();
			for (TreeNode sibling : siblings) {
				descendants.addAll(sibling.children);
			}
			for (TreeNode node : descendants) {
				if (node.getType() == x.getType())
					candidates.add(node);
			}
			if(descendants.size() == 0)
				break;
			siblings = descendants;
			depth++;
		}
		//and ancestors with siblings.
		TreeNode parent = y.getParent();
		depth = 1;
		while (depth <= DEPTH_THRESHOLD) {
			if(parent != null){
				List<TreeNode> ancestors = getSiblings(parent);
				ancestors.add(0, parent);
				for (TreeNode node : ancestors){
					if (node.getType() == x.getType())
						candidates.add(node);
				}
				parent = parent.getParent();
			}else{
				break;
			}
			depth++;
		}
		return candidates;
	}

	private static List<TreeNode> getSiblings(TreeNode node) {
		List<TreeNode> siblings = new ArrayList<TreeNode>();
		if(node.getParent() != null){
			List<TreeNode> nodes = node.getParent().children;
			int index = nodes.indexOf(node);
			int threshold = (int)Math.round(nodes.size() * DIST_THRESHOLD);
			int i = index - 1, j = index + 1;
			int upperBound = index + threshold < nodes.size() ? index + threshold : nodes.size() - 1;
			int lowerBound = index - threshold >= 0 ? index - threshold : 0;
			while(true){
				if(i >= lowerBound)
					siblings.add(nodes.get(i--));
				if(j <= upperBound)
					siblings.add(nodes.get(j++));
				if(j > upperBound && i < lowerBound)
					break;
			}
		}
		return siblings;
	}

	private static void updateMatch(TreeNode beforeNode, TreeNode afterNode) {
		beforeNode.setMatched(afterNode);
		afterNode.setMatched(beforeNode);
	}

	private static int updateChildMatch(TreeNode beforeNode, TreeNode afterNode) {
		int matched = 0;
		for(int i=0; i<beforeNode.children.size(); i++){
			TreeNode bChild = beforeNode.children.get(i);
			TreeNode aChild = afterNode.children.get(i);
			if (!bChild.isMatched() && !aChild.isMatched()) {
				matched += 2;
				updateMatch(bChild, aChild);
				matched += updateChildMatch(bChild, aChild);
			}
		}
		return matched;
	}

}
