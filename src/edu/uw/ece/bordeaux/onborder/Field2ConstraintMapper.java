package edu.uw.ece.bordeaux.onborder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4compiler.ast.Bounds;
import edu.mit.csail.sdg.alloy4compiler.ast.Decl;
import edu.mit.csail.sdg.alloy4compiler.ast.Expr;
import edu.mit.csail.sdg.alloy4compiler.ast.ExprBinary;
import edu.mit.csail.sdg.alloy4compiler.ast.ExprCall;
import edu.mit.csail.sdg.alloy4compiler.ast.ExprConstant;
import edu.mit.csail.sdg.alloy4compiler.ast.ExprFix;
import edu.mit.csail.sdg.alloy4compiler.ast.ExprITE;
import edu.mit.csail.sdg.alloy4compiler.ast.ExprLet;
import edu.mit.csail.sdg.alloy4compiler.ast.ExprList;
import edu.mit.csail.sdg.alloy4compiler.ast.ExprQt;
import edu.mit.csail.sdg.alloy4compiler.ast.ExprUnary;
import edu.mit.csail.sdg.alloy4compiler.ast.ExprVar;
import edu.mit.csail.sdg.alloy4compiler.ast.Func;
import edu.mit.csail.sdg.alloy4compiler.ast.Module;
import edu.mit.csail.sdg.alloy4compiler.ast.Sig;
import edu.mit.csail.sdg.alloy4compiler.ast.Sig.Field;
import edu.mit.csail.sdg.alloy4compiler.ast.VisitReturn;
import edu.mit.csail.sdg.alloy4compiler.ast.ExprUnary.Op;
import edu.mit.csail.sdg.alloy4compiler.translator.A4Solution;
import edu.uw.ece.bordeaux.util.ExtractorUtils;
import edu.uw.ece.bordeaux.util.Utils;

/**
 * Maps fields to their respective declarative constraints
 * @author Fikayo Odunayo
 */
public class Field2ConstraintMapper extends VisitReturn<Object> {

	/**
	 * Gets signature definitions from the given module based on their position (line number)
	 * in the module file.
	 * @param sol - the specified module of interest
	 * @return A string with every unignorable ({@link ExtractorUtils.sigToBeIgnored}) signature definition
	 * @throws Err
	 */
	public static String getSigDeclarationViaPos(Module sol) {
		
		StringBuilder allSigs = new StringBuilder();
		for(Sig sig: sol.getAllReachableSigs()) {
			
			if(ExtractorUtils.sigToBeIgnored(sig)) continue;
			
			String prefix = "";
			if(sig.isAbstract != null) prefix = "abstract ";
			allSigs.append(prefix + "sig " + Utils.readSnippet(sig.span()) + "\n");
		}

		return allSigs.toString();
	}
	
	/**
	 * Gets a map of predicates from the given module based on their position (line number) in the module file.
	 * @param sol - the specified module of interest
	 * @return A map where the key is the pred name and the value is the pred code. Each key can have multiple values due to function overloads
	 * @throws Err
	 */
	public static MultiValuedMap<String, String> getPredDeclarationViaPos(Module sol) {
		
		MultiValuedMap<String, String> preds = new HashSetValuedHashMap<>();
		for(Func func: sol.getAllFunc()) {
			
			if(!func.isPred) continue;

			String codeSnippet = Utils.readSnippet(func.span());
			int bracketIndex = codeSnippet.indexOf('[');
			bracketIndex = bracketIndex == -1 ? codeSnippet.indexOf('{') : bracketIndex;
			
			String predName = func.label;
			
			if(bracketIndex >= 0) {
				int predIndex = codeSnippet.indexOf("pred ");
				if(predIndex >= 0) { 
					predName = codeSnippet.substring(predIndex, bracketIndex);
					predName = predName.replace("pred", "");
				} else {
					predName = codeSnippet.substring(0, bracketIndex);
				}
			}
			
			predName = predName.trim();
			preds.put(predName, codeSnippet);
		}

		return preds;
	}
	

	public static List<String> getAllFuncDeclarationViaPos(Module sol) {
		
		List<String> preds = new ArrayList<>();
		for(Func func: sol.getAllFunc()) {
			
			String codeSnippet = Utils.readSnippet(func.span());
			preds.add(codeSnippet);
		}

		return preds;
	}
	
	public static String getSigDeclations(Module sol) throws Err {
		
		StringBuilder allSigs = new StringBuilder();
		for(Sig sig: sol.getAllReachableSigs()) {
			
			if(sig.builtin) continue;
			
			StringBuilder sigString = new StringBuilder();
			String sigLabel = getCamelCase(sig.label);
			sigString.append(String.format("sig %s {", sigLabel));
			
			for(Decl decl: sig.getFieldDecls()) {

				Field field = (Field)decl.get();
				Expr expr = decl.expr;
				
				String label = sigLabel + "_" + field.label;
				
				Field2ConstraintMapper mapper = new Field2ConstraintMapper();
				Object str = mapper.visitThis(expr);
				String fieldDecl = String.format("%s: %s ", label, str.toString().trim());
				
				sigString.append(String.format("%s, ", fieldDecl));
				
         	}
			
			if(sig.getFieldDecls().isEmpty()) {
				sigString.append("}");
			}
			else {
				sigString.replace(sigString.length() - 2, sigString.length() - 1, "}");
			}
			
			allSigs.append(sigString.toString().replace("this/", "") + "\n");
		}
		
		allSigs.append("\nrun {}");
		return allSigs.toString();
	}
	
	/**
	 * Creates a map of fields to declarative constraints.
	 * 
	 * @param sol - the given {@link A4Solution}
	 * @return a map of {@link Field} to {@link Expr} constraints
	 * @throws Err 
	 */
	public static Map<Field, ?> mapFields(A4Solution sol) throws Err {
		
		HashMap<Field, Object> map = new HashMap<>();
		
		for(Sig sig: sol.getAllReachableSigs()) {
						
			for(Decl decl: sig.getFieldDecls()) {

				Field field = (Field)decl.get();
				Expr expr = decl.expr;
				
				String label = field.label;
				
//				Expr constraint = field.in(decl.expr);
				
				Field2ConstraintMapper mapper = new Field2ConstraintMapper();
				Object str = mapper.visitThis(expr);
				String fieldDecl = String.format("%s: %s ", label, str.toString().trim());
								
				map.put(field, fieldDecl);
				
//				for(ExprHasName n: decl.names) {
//				   	Field field = (Field)n;
//				   	Expr constraint = field.in(decl.expr);
//				   	map.put(field, constraint);
//				}
         	}
		}
		
		return map;
	}

	private static String getCamelCase(String in) {
		if(in == null || in.isEmpty()) return in;
		
		boolean lenG1 = in.length() > 1;
		return Character.toLowerCase(in.charAt(0)) + (lenG1 ? in.substring(1) : "");
	}
	
	@Override
	public Object visit(ExprBinary x) throws Err {

		StringBuilder builder = new StringBuilder();
		switch(x.op) {
		
		case JOIN: {
			
			Object left = visitThis(x.left);
			if(left.toString().trim().equals("this")) {
				
			} else {
				builder.append(String.format("%s%s", left.toString().trim(), x.op));
			}
			
			Object right = visitThis(x.right);
			
			builder.append(String.format("%s ", right.toString().trim()));
			break;
		}
		
		default: {
			
			Object left = visitThis(x.left);
			Object right = visitThis(x.right);
			
			builder.append(String.format("%s %s %s ", left.toString().trim(), x.op, right.toString().trim()));
			break;
		}
		
		}
		
		return builder.toString();
	}

	@Override
	public Object visit(ExprList x) throws Err {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visit(ExprCall x) throws Err {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visit(ExprConstant x) throws Err {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visit(ExprITE x) throws Err {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visit(ExprLet x) throws Err {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visit(ExprQt x) throws Err {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visit(ExprUnary x) throws Err {

		StringBuilder builder = new StringBuilder();
		switch(x.op) {
		
		/* Mult op */
		case SETOF:
		case SOMEOF:
		case LONEOF:
		case ONEOF: {
			Object sub = this.visitThis(x.sub);
			builder.append(String.format("%s %s ", x.op.toString().replace("of", ""), sub.toString().trim()));
			break;
		}
		
		case NOOP: {
			Object sub = visitThis(x.sub);
			builder.append(String.format("%s ", sub.toString().trim()));
			break;
		}
		
		default:
			break;		
		}
		
		return builder.toString();
	}

	@Override
	public Object visit(ExprVar x) throws Err {
		return x.label;
	}

	@Override
	public Object visit(Sig x) throws Err {
		return x.label;
	}

	@Override
	public Object visit(Field x) throws Err {
		return x.label;
	}

	@Override
	public Object visit(Bounds bounds) throws Err {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object visit(ExprFix x) throws Err {
		// TODO Auto-generated method stub
		return null;
	}
	
}
