package script.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class EditScript implements Serializable {
	private static final long serialVersionUID = -5733483402808425064L;
	private List<EditOp> operations;
	public int exactMatch = 0;
	public int similarMatch = 0;
	public int followupMatch = 0;
	public int leafMatch = 0;
	public int exactMatchCount = 0;

	public EditScript(){
		this.operations = new ArrayList<EditOp>();
	}

	public boolean addEditOp(EditOp op){
		return this.operations.add(op);
	}

	public void addEditOps(Collection<? extends EditOp> editOps){
		for(EditOp op : editOps){
			operations.add(op);
		}
	}

	public List<EditOp> getEditOps(){
		return operations;
	}

	public int size(){
		return operations.size();
	}

	@Override
	public String toString(){
		StringBuffer sb = new StringBuffer();
		for(EditOp op : operations){
			sb.append(op.toOpString());
			sb.append("\n");
		}
		return sb.toString();
	}

	public void merge(EditScript script) {
		this.operations.addAll(script.operations);
	}

	public void merge(List<EditScript> scripts) {
		for(EditScript script : scripts){
			this.operations.addAll(script.operations);
		}
	}

	public void updateMatchCount(int exactMatch, int similarMatch, int followupMatch, int leafMatch, int exactMatchCount){
		this.exactMatch = exactMatch;
		this.similarMatch = similarMatch;
		this.followupMatch = followupMatch;
		this.leafMatch = leafMatch;
		this.exactMatchCount = exactMatchCount;
	}
}
