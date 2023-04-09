package main;

import java.io.File;
import java.io.IOException;

import script.ScriptGenerator;
import script.model.EditOp;
import script.model.EditScript;
import tree.Tree;
import tree.TreeBuilder;

public class LAS {

	public static void main(String[] args) {
		File a = null;
		File b = null;
		if(args.length > 0){
			b = new File(args[0]);
			a = new File(args[1]);
		} else {
			System.out.println("You must specify two files to be compared.");
		}
		try {
			Tree before = TreeBuilder.buildTreeFromFile(b);
			Tree after = TreeBuilder.buildTreeFromFile(a);

			EditScript script = ScriptGenerator.generateScript(before, after);
			for(EditOp op : script.getEditOps()){
				System.out.println(op);
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
