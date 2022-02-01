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
		File b2 = new File("/Users/Jindae/Documents/workspace/Toys/src/ABC.java");
		File a2 = new File("/Users/Jindae/Documents/workspace/Toys/src/ABC2.java");
		File b1 = new File("/Users/Jindae/Documents/workspace/Toys/src/Before.java");
		File a1 = new File("/Users/Jindae/Documents/workspace/Toys/src/After.java");
		File b3 = new File("/Volumes/Data/autofix_exp/math/math_906251_904561/before_after/BigFraction_before.java");
		File a3 = new File("/Volumes/Data/autofix_exp/math/math_906251_904561/before_after/BigFraction_after.java");
		File b4 = new File("/Volumes/Data/autofix_exp/benchmark/collections_1141_1140_320589dca3_5ecf769b32/patch_human/buggy/org/apache/commons/collections/keyvalue/MultiKey.java");
		File a4 = new File("/Volumes/Data/autofix_exp/benchmark/collections_1141_1140_320589dca3_5ecf769b32/patch_human/patch/org/apache/commons/collections/keyvalue/MultiKey.java");
		String base = "/Volumes/Data/difftool/subjects/";
		String dir = "rhino/rhino1/";
		String filePath = "Context.java";
		File b = new File(base + dir + "old/" + filePath);
		File a = new File(base + dir + "new/" +filePath);
		b = new File("/Users/Jindae/Documents/workspace/ESScoreCalc/changes/rhino1/old/NativeString.java");
		a = new File("/Users/Jindae/Documents/workspace/ESScoreCalc/changes/rhino1/new/NativeString.java");
		//		b = new File("/Users/Jindae/Documents/workspace/ESScoreCalc/math3/old/ZipfDistribution.java");
		//		a = new File("/Users/Jindae/Documents/workspace/ESScoreCalc/math3/new/ZipfDistribution.java");
		System.setProperty("las.dist.threshold", "0.5");
		System.setProperty("las.depth.threshold", "3");
		//		System.setProperty("las.enable.exact", "false");
		if(args.length > 0){
			b = new File(args[0]);
			a = new File(args[1]);
		}
		try {
			Tree before = TreeBuilder.buildTreeFromFile(b);
			Tree after = TreeBuilder.buildTreeFromFile(a);

			EditScript script = ScriptGenerator.generateScript(before, after);
			//			System.out.println(script);
			for(EditOp op : script.getEditOps()){
				System.out.println(op);
			}
			System.out.println(script.exactMatch);
			System.out.println(script.exactMatchCount);

			before = TreeBuilder.buildTreeFromFile(b2);
			after = TreeBuilder.buildTreeFromFile(a2);
			script = ScriptGenerator.generateScript(before, after);

			System.out.println(script.exactMatch);
			System.out.println(script.exactMatchCount);

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
