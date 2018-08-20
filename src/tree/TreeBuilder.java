package tree;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

public class TreeBuilder {

	public static Tree buildTreeFromFile(File f) throws IOException {
		String source = readContent(f);
		Tree tree = buildTreeFromSource(source);
		tree.setName(f.getName());

		return tree;
	}

	public static Tree buildTreeFromFile(File f,  String[] classPath, String[] sourcePath) throws IOException {
		String source = readContent(f);
		Tree tree = new Tree("");
		CompilationUnit cu = getCompilationUnit(source, classPath, sourcePath);
		JavaCodeVisitor visitor = new JavaCodeVisitor(tree);
		cu.accept(visitor);
		tree.computeDepth();
		tree.computeHashString();
		tree.setName(f.getName());

		return tree;
	}

	public static Tree buildTreeFromSource(String source) throws IOException {
		Tree tree = new Tree("");
		CompilationUnit cu = getCompilationUnit(source);
		JavaCodeVisitor visitor = new JavaCodeVisitor(tree);
		cu.accept(visitor);
		tree.computeDepth();
		tree.computeHashString();

		return tree;
	}

	public static Tree buildTreeFromCompilationUnit(CompilationUnit cu){
		Tree tree = new Tree("");
		JavaCodeVisitor visitor = new JavaCodeVisitor(tree);
		cu.accept(visitor);
		tree.computeDepth();
		tree.computeHashString();

		return tree;
	}

	public static CompilationUnit getCompilationUnit(String source, String[] classPath, String[] sourcePath){
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setEnvironment(classPath, sourcePath, null, true);
		Map options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
		parser.setCompilerOptions(options);
		parser.setSource(source.toCharArray());
		parser.setResolveBindings(true);
		parser.setUnitName("Temp.java");
		CompilationUnit cu = (CompilationUnit)parser.createAST(null);

		return cu;
	}

	public static CompilationUnit getCompilationUnit(File f) throws IOException {
		return getCompilationUnit(readContent(f));
	}

	public static CompilationUnit getCompilationUnit(String source) {
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
		parser.setCompilerOptions(options);
		parser.setSource(source.toCharArray());
		CompilationUnit cu = (CompilationUnit)parser.createAST(null);

		return cu;
	}

	private static String readContent(File f) throws IOException {
		StringBuffer sb = new StringBuffer();
		String content;
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
		char[] cbuf = new char[500];
		int len = 0;
		while((len=br.read(cbuf))>-1){
			sb.append(cbuf, 0, len);
		}
		br.close();
		content = sb.toString();
		return content;
	}

}
