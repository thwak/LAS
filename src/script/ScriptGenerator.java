package script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;

import script.model.Delete;
import script.model.EditOp;
import script.model.EditScript;
import script.model.Insert;
import script.model.Move;
import script.model.Replace;
import script.model.Update;
import tree.Tree;
import tree.TreeNode;

public class ScriptGenerator {

	private static final double DIST_THRESHOLD = System.getProperty("las.dist.threshold") == null ? 0.5d : Double.parseDouble(System.getProperty("las.dist.threshold"));
	private static final int DEPTH_THRESHOLD = System.getProperty("las.depth.threshold") == null ? 3 : Integer.parseInt(System.getProperty("las.depth.threshold"));
	private static final double SIM_THRESHOLD = System.getProperty("las.sim.threshold") == null ? 0.65d : Double.parseDouble(System.getProperty("las.sim.threshold"));
	private static final boolean ENABLE_EXACT_MATCH =  System.getProperty("las.enable.exact") == null ? true : Boolean.parseBoolean(System.getProperty("las.enable.exact"));
	private static final boolean ENABLE_REPLACE =  System.getProperty("las.enable.replace") == null ? true : Boolean.parseBoolean(System.getProperty("las.enable.replace"));

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
		Stack<EditOp> opStack = new Stack<>();
		for(TreeNode node : before.getRoot().children){
			script.addEditOps(generateDelete(node, opStack));
		}

		//Then generate insert, update.
		opStack.clear();
		for(TreeNode node : after.getRoot().children){
			script.addEditOps(generateInsertMoveUpdate(node, opStack));
		}

		//If ENABLE_REPLACE is on, convert insert-delete pairs into Replace.
		if(ENABLE_REPLACE)
			generateReplace(script);

		//Finally, generate move operations for ordering changes.
		script.addEditOps(generateOrderingChange(before.getRoot()));

		return script;
	}

	private static void generateReplace(EditScript script) {
		Map<EditOp, EditOp> pairs = new HashMap<>();
		List<Insert> inserts = new ArrayList<>();
		List<Delete> deletes = new ArrayList<>();
		List<Move> moves = new ArrayList<>();
		List<Update> updates = new ArrayList<>();
		for(EditOp op : script.getEditOps()) {
			if(op instanceof Insert) {
				inserts.add((Insert)op);
			} else if(op instanceof Delete) {
				deletes.add((Delete)op);
			} else if(op instanceof Move) {
				moves.add((Move)op);
			} else if(op instanceof Update) {
				updates.add((Update)op);
			}
		}

		//Find replace candidates connected by Moves.
		for(Move mov : moves) {
			Delete del = findEditOp(mov.getNode(), deletes);
			Insert ins = findEditOp(mov.getNode().getMatched(), inserts);
			if(del != null && ins != null) {
				if(!pairs.containsKey(del) && !pairs.containsKey(ins)
						&& verifyLoc(del.getNode(), ins.getNode(), false)) {
					pairs.put(del, ins);
					pairs.put(ins, del);
				}
				pairs.put(mov, del);
			} else if(del != null) {
				if(!pairs.containsKey(del)
						&& verifyLoc(del.getNode(), mov.getNode().getMatched(), false)) {
					pairs.put(del, mov);
				} else {
					pairs.put(mov, del);
				}
			} else if(ins != null) {
				if(!pairs.containsKey(ins)
						&& verifyLoc(ins.getNode(), mov.getNode(), false)) {
					pairs.put(ins, mov);
				} else {
					pairs.put(mov, ins);
				}
			}
		}

		//Processing replace candidates.
		for(EditOp op : pairs.keySet()) {
			EditOp op2 = pairs.get(op);
			script.removeEditOp(op);
			if(op instanceof Delete) {
				Delete del = (Delete)op;
				deletes.remove(del);
				script.removeEditOp(op2);
				if(op2 instanceof Insert) {
					Replace r = new Replace(del.getNode(), op2.getNode());
					discardUpdates(del, updates, script);
					script.addEditOp(r);
				} else if(op2 instanceof Move) {
					Replace r = new Replace(del.getNode(), op2.getNode().getMatched());
					discardUpdates(del, updates, script);
					script.addEditOp(r);
				}
			} else if(op instanceof Insert) {
				inserts.remove(op);
				script.removeEditOp(op2);
				if(op2 instanceof Move) {
					Replace r = new Replace(op2.getNode(), op.getNode());
					discardUpdates(op, updates, script);
					script.addEditOp(r);
				}
			} else if(op instanceof Move) {
				discardMove((Move)op, pairs, script);
				discardUpdates(op, updates, script);
			}
		}

		//Check remaining deletes and inserts.
		for(Delete del : deletes) {
			Iterator<Insert> it = inserts.iterator();
			while(it.hasNext()) {
				Insert ins = it.next();
				if(verifyLoc(del.getNode(), ins.getNode(), true)) {
					script.removeEditOp(del);
					script.removeEditOp(ins);
					it.remove();
					Replace r = new Replace(del.getNode(), ins.getNode());
					discardUpdates(del, updates, script);
					script.addEditOp(r);
					break;
				}
			}
		}
	}

	private static void discardUpdates(EditOp op, List<Update> updates, EditScript script) {
		Iterator<Update> it = updates.iterator();
		while(it.hasNext()) {
			Update upd = it.next();
			TreeNode node = op instanceof Insert ? upd.getLocation() : upd.getNode();
			if(descCheck(op.getNode(), node)) {
				it.remove();
				script.removeEditOp(upd);
			}
		}
	}

	private static boolean descCheck(TreeNode node, TreeNode descendant) {
		TreeNode parent = descendant.getParent();
		while(parent != null) {
			if(node == parent) {
				return true;
			}
			parent = parent.getParent();
		}
		return false;
	}

	private static void discardMove(Move mov, Map<EditOp, EditOp> pairs, EditScript script) {
		EditOp op = pairs.get(mov);
		//If op is combined to a replace.
		if(pairs.containsKey(op)) {
			if(op instanceof Delete) {
				//if mov is a part of a delete, discard the deleted and add the inserted part.
				script.addEditOp(new Insert(mov.getNode().getMatched()));
				mov.getNode().getMatched().unmatch();
				mov.getNode().unmatch();
			} else if(op instanceof Insert) {
				script.addEditOp(new Delete(mov.getNode()));
				mov.getNode().getMatched().unmatch();
				mov.getNode().unmatch();
			}
		}
	}

	private static boolean verifyLoc(TreeNode node1, TreeNode node2, boolean typeCheck) {
		if(node1.getParent() == null || node2.getParent() == null
				|| node1.getParent().getMatched() != node2.getParent())
			return false;
		StructuralPropertyDescriptor loc1 = identifyLocation(node1);
		StructuralPropertyDescriptor loc2 = identifyLocation(node2);
		if(loc1 != null && loc2 != null && loc1.equals(loc2)) {
			if (typeCheck && loc1.getId().equals("statements")
					&& node1.getType() != node2.getType()) {
				return false;
			}
			if (loc1.isChildListProperty() && !loc1.getId().equals("statements")
					&& node1.indexInParent() != node2.indexInParent()) {
				return false;
			}
			if(loc1.getId().equals("statements")) {
				int leftCount1 = getMatchedLeftCount(node1);
				int leftCount2 = getMatchedLeftCount(node2);
				TreeNode left1 = null;
				TreeNode left2 = null;
				if(leftCount1 >= leftCount2) {
					left1 = getMatchedLeft(node1, leftCount1-leftCount2);
					left2 = getMatchedLeft(node2, 0);
				} else {
					left1 = getMatchedLeft(node1, 0);
					left2 = getMatchedLeft(node2, leftCount2-leftCount1);
				}
				if(left1 == null && left2 == null ||
						left1 != null && left1.getMatched() == left2)
					return true;
				else
					return false;
			}
			return true;
		}
		return false;
	}

	private static TreeNode getMatchedLeft(TreeNode node, int offset) {
		TreeNode left = node.getLeft();
		while(left != null && offset > 0) {
			offset--;
			left = left.getLeft();
		}
		return left;
	}

	private static int getMatchedLeftCount(TreeNode node) {
		int count = 0;
		TreeNode left = node.getLeft();
		while(left != null) {
			if(left.isMatched())
				count++;
			left = left.getLeft();
		}
		return count;
	}

	private static StructuralPropertyDescriptor identifyLocation(TreeNode node) {
		StructuralPropertyDescriptor loc;
		if(node.getASTNode().getParent().getNodeType() == ASTNode.EXPRESSION_STATEMENT)
			loc = node.getASTNode().getParent().getLocationInParent();
		else
			loc = node.getASTNode().getLocationInParent();
		return loc;
	}

	private static <T extends EditOp> T findEditOp(TreeNode node, List<T> operations) {
		if(node.getParent().isMatched())
			return null;
		while(node.getParent() != null) {
			if(!node.getParent().isMatched())
				node = node.getParent();
			else
				break;
		}
		for(T op : operations) {
			if(op.getNode().equals(node))
				return op;
		}
		return null;
	}

	private static List<EditOp> generateOrderingChange(TreeNode node) {
		List<EditOp> editOps = new ArrayList<>();
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
		List<TreeNode> nonLCSNodes = new ArrayList<>();
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
		List<EditOp> editOps = new ArrayList<>();
		boolean isPushed = false;
		if(node.isMatched()){
			TreeNode parent = node.getParent();
			TreeNode parentOfMatched = node.getMatched().getParent();
			if(parent != null
					&& parent.getMatched() != parentOfMatched){
				node.setChangeType(TreeNode.NODE_INSERTED);
				node.getMatched().setChangeType(TreeNode.NODE_DELETED);
				Move move = new Move(node.getMatched(), node.getParent(), node.indexInParent());
				//If a subtree is moved to an inserted node, so need to check op type.
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
			addInsert(node, opStack, editOps);
			isPushed = true;
		}
		for(TreeNode child : node.children){
			editOps.addAll(generateInsertMoveUpdate(child, opStack));
		}
		if(isPushed)
			opStack.pop();
		return editOps;
	}

	private static void addInsert(TreeNode node, Stack<EditOp> opStack, List<EditOp> editOps) {
		Insert insert = new Insert(node);
		if(!opStack.isEmpty() && opStack.peek() instanceof Insert){
			opStack.peek().addEditOp(insert);
		}else{
			editOps.add(insert);
		}
		opStack.push(insert);
	}

	/**
	 * Generate delete operations for each deleted subtree.
	 *
	 * @param node the root of subtree.
	 * @param opStack a stack of delete operations currently processing.
	 * @return a list of delete operations.
	 */
	private static List<? extends EditOp> generateDelete(TreeNode node, Stack<EditOp> opStack) {
		List<EditOp> delOps = new ArrayList<>();
		if(!node.isMatched()){
			node.setChangeType(TreeNode.NODE_DELETED);
			addDeleted(node, opStack, delOps);
		}
		for(TreeNode child : node.children){
			delOps.addAll(generateDelete(child, opStack));
		}
		if(!node.isMatched())
			opStack.pop();
		return delOps;
	}

	private static void addDeleted(TreeNode node, Stack<EditOp> opStack, List<EditOp> delOps) {
		Delete delete = new Delete(node);
		//If opStack is empty, it is the root of a deleted subtree.
		if(opStack.empty()){
			delOps.add(delete);
		}else{
			opStack.peek().addEditOp(delete);
		}
		opStack.push(delete);
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
		Map<String, Integer> leafCount = new HashMap<>();
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
		List<TreeNode> xChildren = new ArrayList<>();
		List<TreeNode> yChildren = new ArrayList<>();
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
				List<TreeNode> unmatchedLeaves = new ArrayList<>();
				//Get unmatched leaves.
				for (TreeNode child : node.getUnmatchedChildren()) {
					if (child.isLeaf()) {
						unmatchedLeaves.add(child);
					}
				}
				//Find exact match first.
				List<TreeNode> candidates = node.getMatched().getUnmatchedChildren();
				List<TreeNode> matched = new ArrayList<>();
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
		List<TreeNode> xChildren = new ArrayList<>();
		List<TreeNode> yChildren = new ArrayList<>();
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
		List<TreeNode> candidates = new ArrayList<>();
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
			List<TreeNode> descendants = new ArrayList<>();
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
		List<TreeNode> siblings = new ArrayList<>();
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
