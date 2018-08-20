package tree;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TreeVector implements Serializable {

	private static final long serialVersionUID = -8402268305119253330L;
	private Map<String, Integer> vector;
	private int sum;

	public TreeVector(){
		vector = new HashMap<String, Integer>();
		sum = 0;
	}

	public TreeVector(String label){
		this();
		vector.put(label, 1);
	}

	public void update(TreeNode node){
		if(vector.containsKey(node.getLabel())){
			vector.put(node.getLabel(), vector.get(node.getLabel()));
		}else{
			vector.put(node.getLabel(), 1);
		}
		for(TreeNode child : node.children){
			update(child);
		}
	}

	public Set<String> keySet(){
		return vector.keySet();
	}

	public void update(TreeVector vec){
		for(String key : vec.keySet()){
			if(this.vector.containsKey(key)){
				this.vector.put(key, this.getValue(key) + vec.getValue(key));
			}else{
				this.vector.put(key, vec.getValue(key));
			}
		}
	}

	public int getValue(String key){
		return vector.get(key);
	}

	public double similarity(TreeVector vec){
		int commonNodes = 0;
		Set<String> commonKeys = new HashSet<String>(this.keySet());
		commonKeys.retainAll(vec.keySet());
		for (String key : commonKeys) {
			commonNodes += Math.min(this.getValue(key), vec.getValue(key));
		}
		int totalNodes = this.sum() + vec.sum();
		return 2.0d*commonNodes/totalNodes;
	}

	public int sum(){
		if(sum == 0){
			for(Integer val : vector.values()){
				sum += val;
			}
		}
		return sum;
	}

	@Override
	public String toString(){
		StringBuffer sb = new StringBuffer("[");
		for (String key : keySet()) {
			sb.append(" ");
			sb.append(vector.get(key));
		}
		sb.append(" ]");
		return sb.toString();
	}
}
