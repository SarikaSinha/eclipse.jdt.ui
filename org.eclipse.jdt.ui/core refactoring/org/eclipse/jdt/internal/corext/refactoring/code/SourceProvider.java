/*******************************************************************************
 * Copyright (c) 2002 International Business Machines Corp. and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0 
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.code;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.textmanipulation.MultiTextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.RangeMarker;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBuffer;
import org.eclipse.jdt.internal.corext.textmanipulation.TextBufferEditor;
import org.eclipse.jdt.internal.corext.textmanipulation.TextEdit;
import org.eclipse.jdt.internal.corext.textmanipulation.TextRange;
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil;
import org.eclipse.jdt.internal.corext.util.Strings;

public class SourceProvider {

	private ICompilationUnit fCUnit;
	private TextBuffer fBuffer;
	private MethodDeclaration fDeclaration;
	private ASTRewrite fRewriter;
	private SourceAnalyzer fAnalyzer;
	private boolean fEvaluateReturnValue;
	
	private static class ReturnAnalyzer extends ASTVisitor {
		public boolean evalReturnValue= false;
		public boolean visit(ReturnStatement node) {
			Expression expression= node.getExpression();
			if (!(ASTNodes.isLiteral(expression) || expression instanceof Name)) {
				evalReturnValue= true;
			}
			return false;
		}
	}

	public SourceProvider(ICompilationUnit unit, MethodDeclaration declaration) throws JavaModelException {
		super();
		fCUnit= unit;
		fBuffer= TextBuffer.create(unit.getBuffer().getContents());
		fDeclaration= declaration;
		List parameters= fDeclaration.parameters();
		for (Iterator iter= parameters.iterator(); iter.hasNext();) {
			SingleVariableDeclaration element= (SingleVariableDeclaration)iter.next();
			ParameterData data= new ParameterData(element);
			element.setProperty(ParameterData.PROPERTY, data);
		}
		fRewriter= new ASTRewrite(fDeclaration);
		fAnalyzer= new SourceAnalyzer(fCUnit, fDeclaration);
	}

	public RefactoringStatus checkActivation() throws JavaModelException {
		return fAnalyzer.checkActivation();
	}
	
	public void initialize() {
		fAnalyzer.analyzeParameters();
		if (hasReturnValue()) {
			ASTNode last= getLastStatement();
			if (last != null) {
				ReturnAnalyzer analyzer= new ReturnAnalyzer();
				last.accept(analyzer);
				fEvaluateReturnValue= analyzer.evalReturnValue;
			}
		}
	}

	public boolean isExecutionFlowInterrupted() {
		return fAnalyzer.isExecutionFlowInterrupted();
	}
	
	public boolean hasReturnValue() {
		IMethodBinding binding= fDeclaration.resolveBinding();
		return binding.getReturnType() != fDeclaration.getAST().resolveWellKnownType("void");
	}
	
	public boolean mustEvaluateReturnValue() {
		return fEvaluateReturnValue;
	}
	
	public int getNumberOfStatements() {
		return fDeclaration.getBody().statements().size();
	}
	
	public MethodDeclaration getDeclaration() {
		return fDeclaration;
	}
	
	public String getMethodName() {
		return fDeclaration.getName().getIdentifier();
	}
	
	public ITypeBinding getReturnType() {
		return fDeclaration.resolveBinding().getReturnType();
	}
	
	public ParameterData getParameterData(int index) {
		SingleVariableDeclaration decl= (SingleVariableDeclaration)fDeclaration.parameters().get(index);
		return (ParameterData)decl.getProperty(ParameterData.PROPERTY);
	}
	
	public ICompilationUnit getCompilationUnit() {
		return fCUnit;
	}
	
	public boolean needsReturnedExpressionParenthesis() {
		ASTNode last= getLastStatement();
		if (last instanceof ReturnStatement) {
			return ASTNodes.needsParenthesis(((ReturnStatement)last).getExpression());
		}
		return false;
	}
	
	public int getReceiversToBeUpdated() {
		return fAnalyzer.getImplicitReceivers().size();
	}
	
	public String[] getCodeBlocks(CallContext context) throws CoreException {
		List result= new ArrayList(1);
		
		replaceParameterWithExpression(context.arguments);
		updateImplicitReceivers(context);
		makeNamesUnique(context.usedCallerNames);
		
		List ranges= null;
		if (hasReturnValue()) {
			if (context.callMode == ASTNode.RETURN_STATEMENT) {
				ranges= getStatementRanges();
			} else {
				ranges= getExpressionRanges();
			}
		} else {
			ASTNode last= getLastStatement();
			if (last != null && last.getNodeType() == ASTNode.RETURN_STATEMENT) {
				ranges= getReturnStatementRanges();
			} else {
				ranges= getStatementRanges();
			}
		}
		
		MultiTextEdit dummy= new MultiTextEdit();
		fRewriter.rewriteNode(fBuffer, dummy, null);

		int size= ranges.size();
		RangeMarker[] markers= new RangeMarker[size];
		for (int i= 0; i < markers.length; i++) {
			markers[i]= new RangeMarker((TextRange)ranges.get(i));
		}
		int split= size <= 1 ? Integer.MAX_VALUE : ((TextRange)ranges.get(0)).getExclusiveEnd();
		TextEdit[] edits= dummy.removeAll();
		for (int i= 0; i < edits.length; i++) {
			TextEdit edit= edits[i];
			int pos= edit.getTextRange().getOffset() >= split ? 1 : 0;
			markers[pos].add(edit);
		}
		MultiTextEdit root= new MultiTextEdit();
		root.addAll(markers);
		TextBufferEditor editor= new TextBufferEditor(fBuffer);
		editor.add(root);
		editor.performEdits(null);
		fRewriter.removeModifications();
		
		return getBlocks(ranges);
	}

	public void replaceParameterWithExpression(String[] expressions) {
		for (int i= 0; i < expressions.length; i++) {
			String expression= expressions[i];
			ParameterData parameter= getParameterData(i);
			List references= parameter.references();
			for (Iterator iter= references.iterator(); iter.hasNext();) {
				ASTNode element= (ASTNode) iter.next();
				ASTNode newNode= fRewriter.createPlaceholder(expression, ASTRewrite.getPlaceholderType(element));
				fRewriter.markAsReplaced(element, newNode);
			}
		}
	}

	private void makeNamesUnique(List usedCallerNames) {
		Collection usedCalleeNames= fAnalyzer.getUsedNames();
		for (Iterator iter= usedCalleeNames.iterator(); iter.hasNext();) {
			SourceAnalyzer.NameData nd= (SourceAnalyzer.NameData) iter.next();
			if (usedCallerNames.contains(nd.getName())) {
				String newName= proposeName(usedCallerNames, nd.getName());
				List references= nd.references();
				for (Iterator refs= references.iterator(); refs.hasNext();) {
					SimpleName element= (SimpleName) refs.next();
					ASTNode newNode= fRewriter.createPlaceholder(newName, ASTRewrite.EXPRESSION);
					fRewriter.markAsReplaced(element, newNode);
				}
			}
		}
	}
	
	private void updateImplicitReceivers(CallContext context) {
		if (context.receiver == null)
			return;
		List implicitReceivers= fAnalyzer.getImplicitReceivers();
		for (Iterator iter= implicitReceivers.iterator(); iter.hasNext();) {
			ASTNode node= (ASTNode)iter.next();
			if (node instanceof MethodInvocation) {
				final MethodInvocation inv= (MethodInvocation)node;
				inv.setExpression(createReceiver(context, (IMethodBinding)inv.getName().resolveBinding()));
			} else if (node instanceof ClassInstanceCreation) {
				final ClassInstanceCreation inst= (ClassInstanceCreation)node;
				inst.setExpression(createReceiver(context, inst.resolveConstructorBinding()));
			} else if (node instanceof Expression) {
				fRewriter.markAsReplaced(node, fRewriter.createPlaceholder(context.receiver, ASTRewrite.EXPRESSION));
			}
		}
	}

	private Expression createReceiver(CallContext context, IMethodBinding method) {
		String receiver= context.receiver;
		if (!context.receiverIsStatic && Modifier.isStatic(method.getModifiers())) {
			receiver= context.importer.addImport(
				Bindings.getFullyQualifiedImportName(fDeclaration.resolveBinding().getDeclaringClass())); 
		}
		Expression exp= (Expression)fRewriter.createPlaceholder(receiver, ASTRewrite.EXPRESSION);
		fRewriter.markAsInserted(exp);
		return exp;
	}
	
	private ASTNode getLastStatement() {
		List statements= fDeclaration.getBody().statements();
		if (statements.isEmpty())
			return null;
		return (ASTNode)statements.get(statements.size() - 1);
	}

	private String proposeName(List used, String start) {
		int i= 1;
		String result= start + i++;
		while (used.contains(result)) {
			result= start + i++;
		}
		return result;
	}

	private List getReturnStatementRanges() {
		List result= new ArrayList(1);
		List statements= fDeclaration.getBody().statements();
		int size= statements.size();
		if (size <= 1)
			return result;
		result.add(createRange(statements, size - 2));
		return result;
	}

	private List getStatementRanges() {
		List result= new ArrayList(1);
		List statements= fDeclaration.getBody().statements();
		int size= statements.size();
		if (size == 0)
			return result;
		result.add(createRange(statements, size - 1));
		return result;
	}

	private List getExpressionRanges() {
		List result= new ArrayList(2);
		List statements= fDeclaration.getBody().statements();
		ReturnStatement rs= null;
		int size= statements.size();
		ASTNode node;
		switch (size) {
			case 0:
				return result;
			case 1:
				node= (ASTNode)statements.get(0);
				if (node.getNodeType() == ASTNode.RETURN_STATEMENT) {
					rs= (ReturnStatement)node;
				} else {
					result.add(TextRange.createFromStartAndLength(node.getStartPosition(), node.getLength()));
				}
				break;
			default: {
				node= (ASTNode)statements.get(size - 1);
				if (node.getNodeType() == ASTNode.RETURN_STATEMENT) {
					result.add(createRange(statements, size - 2));
					rs= (ReturnStatement)node;
				} else {
					result.add(createRange(statements, size - 1));
				}
				break;
			}
		}
		if (rs != null) {
			Expression exp= rs.getExpression();
			result.add(TextRange.createFromStartAndLength(exp.getStartPosition(), exp.getLength()));
		}
		return result;
	}
	
	private TextRange createRange(List statements, int end) {
		int start= ((ASTNode)statements.get(0)).getStartPosition();
		ASTNode last= (ASTNode)statements.get(end);
		int length = last.getStartPosition() - start + last.getLength();
		TextRange range= TextRange.createFromStartAndLength(start, length);
		return range;
	}
	
	private String[] getBlocks(List ranges) {
		int size= ranges.size();
		List result= new ArrayList(size);
		for (int i= 0; i < size; i++) {
			TextRange range= (TextRange)ranges.get(i);
			String content= fBuffer.getContent(range.getOffset(), range.getLength());
			String lines[]= Strings.convertIntoLines(content);
			Strings.trimIndentation(lines, CodeFormatterUtil.getTabWidth(), false);
			result.add(Strings.concatenate(lines, fBuffer.getLineDelimiter()));
		}
		return (String[]) result.toArray(new String[result.size()]);
	}
}
