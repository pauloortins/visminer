package org.visminer.metric;

import java.beans.Expression;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;
import org.visminer.utility.DetailAST;

public class EC implements IMetric<String> {

	private final String NAME = "EC";
	private final String DESCRIPTION = "This metric calculates efferent coupling";		
	private final ArrayList<String> Libs = new ArrayList<String>();										
	private ArrayList<SoftwareType> types;	
	
	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getDescription() {
		return DESCRIPTION;
	}

	@Override
	public String calculate(byte[] data, String path) {
		
		types = new ArrayList<SoftwareType>();
		
		String content = new String(data);		
		
		ASTParser parser = ASTParser.newParser(AST.JLS4);

		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(content.toCharArray());
		
		@SuppressWarnings("unchecked")
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_6);
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM,
				JavaCore.VERSION_1_6);
		options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_6);		
		
		String unitName = "/" + path;
		parser.setUnitName(unitName);
		
		searchFile(new File("/Users/pauloortins/Documents/dev/github/MeuVisminer/Visminer/lib"), ".jar", Libs);
				
		String[] classPaths = Libs.toArray(new String[Libs.size()]);

		parser.setEnvironment(classPaths, new String[] {"/Users/pauloortins/Documents/dev/github/MeuVisminer/Visminer/src"},
				new String[] {"UTF-8"}, false);
		parser.setBindingsRecovery(true);		
		parser.setResolveBindings(true);
		parser.setCompilerOptions(options);
		parser.setStatementsRecovery(true);

		CompilationUnit ast = (CompilationUnit) parser.createAST(null);				
		
		for(int i = 0; i < ast.types().size(); i++){
			TypeDeclaration type = (TypeDeclaration) ast.types().get(i);
			
			SoftwareType softwareType = new SoftwareType();					
			String packageName = ast.getPackage() != null ? ast.getPackage().getName().toString() + "." : "";
			
			softwareType.setName(packageName + type.getName().getFullyQualifiedName());
			
			for(MethodDeclaration method : type.getMethods()) {
								
				SoftwareMethod softwareMethod = new SoftwareMethod();				
				softwareMethod.setName(method.getName().getFullyQualifiedName());
				
				Block body = method.getBody();
				ArrayList<String> calledMethods = processBlock(body);
				for (int j = 0; j < calledMethods.size(); j++) {
					String calledMethod = calledMethods.get(j);
					
					// Se não foi resolvido o binding, o método é da própria class
					
					if (calledMethod.indexOf('.') == -1) {
						softwareMethod.addCalledMethod(softwareType.getName() + "." + calledMethod);
					} else {
						softwareMethod.addCalledMethod(calledMethod);
					}
				}				
				
				softwareType.addMethod(softwareMethod);
			}			
			
			types.add(softwareType);
		}
		
		try {
			return JsonWriter.objectToJson(types.toArray());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return "Efferent Coupling Extraction Error";
	}
	
	private ArrayList<String> processBlock(Block body){
		
		ArrayList<String> calledMethods = new ArrayList<String>();
		
		if(body == null) {
			return calledMethods;
		}
		
		@SuppressWarnings("unchecked")
		List<Statement> statements = body.statements();
		if(statements == null){
			return calledMethods;
		}
		
		for(Statement statement : statements){
			calledMethods.addAll(processStatement(statement));
		}
		
		return calledMethods;		
	}	
	
	private ArrayList<String> processStatement(Statement statement){
		
		ArrayList<String> calledMethods = new ArrayList<String>();
		
		switch(statement.getNodeType()){
		
			case ASTNode.EXPRESSION_STATEMENT: {
				ExpressionStatement expressionStatement = (ExpressionStatement) statement;
				org.eclipse.jdt.core.dom.Expression nextStatement = expressionStatement.getExpression();
					
				while (nextStatement instanceof MethodInvocation) {														
					MethodInvocation methodInvocation = (MethodInvocation) nextStatement;
					
					// Same class call
					if (methodInvocation.getExpression() == null) {
						IBinding methodBinding = methodInvocation.getName().resolveBinding();
						calledMethods.add(methodBinding != null ? methodBinding.getName().toString() : methodInvocation.getName().toString());
					} else {
					
						ITypeBinding entityBinding = methodInvocation.getExpression().resolveTypeBinding();
						IBinding methodBinding = methodInvocation.getName().resolveBinding();
						if (entityBinding != null && methodBinding != null) {
							calledMethods.add(entityBinding.getBinaryName() + "." + methodBinding.getName());							
						}
					}
					nextStatement = methodInvocation.getExpression();
				}
				break;
			}
			
			case ASTNode.VARIABLE_DECLARATION_STATEMENT: {
				VariableDeclarationStatement declarationStatement = (VariableDeclarationStatement) statement;
				break;
			}
			
			case ASTNode.TRY_STATEMENT: {
				TryStatement blockStatement = (TryStatement) statement;
				calledMethods.addAll(processBlock(blockStatement.getBody()));				
				break;
			}
			
			case ASTNode.FOR_STATEMENT: {
				ForStatement blockStatement = (ForStatement) statement;
				
				if (blockStatement.getBody() instanceof Block) {
					calledMethods.addAll(processBlock( (Block) blockStatement.getBody()));
				} else {
					calledMethods.addAll(processStatement(blockStatement.getBody()));
				}				
				
				break;
			}
			
			case ASTNode.DO_STATEMENT: {
				DoStatement blockStatement = (DoStatement) statement;
				
				if (blockStatement.getBody() instanceof Block) {
					calledMethods.addAll(processBlock( (Block) blockStatement.getBody()));
				} else {
					calledMethods.addAll(processStatement(blockStatement.getBody()));
				}				
				
				break;
			}
			
			case ASTNode.IF_STATEMENT: {
				IfStatement blockStatement = (IfStatement) statement;
				
				if (blockStatement.getThenStatement() instanceof Block) {
					calledMethods.addAll(processBlock( (Block) blockStatement.getThenStatement()));
				} else {
					calledMethods.addAll(processStatement(blockStatement.getThenStatement()));
				}		
				
				if (blockStatement.getElseStatement() != null) {
					Statement elseStatement = blockStatement.getElseStatement();
					
					if (elseStatement instanceof Block) {
						calledMethods.addAll(processBlock( (Block) blockStatement.getThenStatement()));
					} else {
						calledMethods.addAll(processStatement(blockStatement.getThenStatement()));
					}
				}
				
				break;
			}
			
			case ASTNode.WHILE_STATEMENT: {
				WhileStatement blockStatement = (WhileStatement) statement;
				
				if (blockStatement.getBody() instanceof Block) {
					calledMethods.addAll(processBlock( (Block) blockStatement.getBody()));
				} else {
					calledMethods.addAll(processStatement(blockStatement.getBody()));
				}				
				
				break;
			}
		}
		
		return calledMethods;
	}
	
	public void searchFile(File dir, String match, ArrayList<String> list) {
	      File[] subdirs=dir.listFiles();
	      for(File subdir: subdirs) {
	         if (subdir.isDirectory()) {
	        	 searchFile(subdir, match, list);
	         } else {
	            doFile(subdir, match, list);
	         }
	      }
	   }
	 
	public  void doFile(File file, String match, ArrayList<String> list) {
		if (file.getAbsolutePath().endsWith(match)) {
			list.add(file.getAbsolutePath());
		}
	}
}
