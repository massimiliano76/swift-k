package org.griphyn.vdl.engine;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.xml.namespace.QName;

import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlFloat;
import org.apache.xmlbeans.XmlInt;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlString;
import org.globus.swift.language.ActualParameter;
import org.globus.swift.language.ApplicationBinding;
import org.globus.swift.language.Array;
import org.globus.swift.language.Assign;
import org.globus.swift.language.BinaryOperator;
import org.globus.swift.language.Binding;
import org.globus.swift.language.Call;
import org.globus.swift.language.Continue;
import org.globus.swift.language.Dataset;
import org.globus.swift.language.Foreach;
import org.globus.swift.language.FormalParameter;
import org.globus.swift.language.Function;
import org.globus.swift.language.If;
import org.globus.swift.language.Iterate;
import org.globus.swift.language.LabelledBinaryOperator;
import org.globus.swift.language.Procedure;
import org.globus.swift.language.ProgramDocument;
import org.globus.swift.language.Range;
import org.globus.swift.language.Switch;
import org.globus.swift.language.UnlabelledUnaryOperator;
import org.globus.swift.language.Variable;
import org.globus.swift.language.Dataset.Mapping;
import org.globus.swift.language.Dataset.Mapping.Param;
import org.globus.swift.language.If.Else;
import org.globus.swift.language.If.Then;
import org.globus.swift.language.Procedure;
import org.globus.swift.language.ProgramDocument.Program;
import org.globus.swift.language.StructureMember;
import org.globus.swift.language.Switch.Case;
import org.globus.swift.language.Switch.Default;
import org.globus.swift.language.TypesDocument.Types;
import org.safehaus.uuid.UUIDGenerator;
import org.w3c.dom.Node;

public class Karajan {
	public static final Logger logger = Logger.getLogger(Karajan.class);
	
	public static final String TEMPLATE_FILE_NAME = "Karajan.stg"; 

	/** an arbitrary statement identifier. Start at some high number to
	    aid visual distinction in logs, but the actual value doesn't
		matter. */
	int callID = 88000;

	StringTemplateGroup m_templates;

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.err.println("Please provide a SwiftScript program file.");
			System.exit(1);
		}
		compile(args[0], System.out);
	}
	
	public static void compile(String in, PrintStream out) throws Exception {
		Karajan me = new Karajan();
		StringTemplateGroup templates = new StringTemplateGroup(new InputStreamReader(
				Karajan.class.getClassLoader().getResource(TEMPLATE_FILE_NAME).openStream()));

		ProgramDocument programDoc = parseProgramXML(in);

		Program prog = programDoc.getProgram();

		me.setTemplateGroup(templates);
		StringTemplate code = me.program(prog);
		out.println(code.toString());
	}

	public static ProgramDocument parseProgramXML(String defs) 
		throws XmlException, IOException {

		XmlOptions options = new XmlOptions();
		Collection errors = new ArrayList();
		options.setErrorListener(errors);
		options.setValidateOnSet();
		options.setLoadLineNumbers();

		ProgramDocument programDoc;
		programDoc  = ProgramDocument.Factory.parse(new File(defs), options);

		if(programDoc.validate(options)) {
			logger.info("Validation of XML intermediate file was successful");
		} else {
			logger.warn("Validation of XML intermediate file failed.");
				// these errors look rather scary, so output them at
				// debug level
			logger.debug("Validation errors:");
			Iterator i = errors.iterator();
			while(i.hasNext()) {
				XmlError error = (XmlError) i.next();
				logger.debug(error.toString());
			}
			System.exit(3);
		}
		return programDoc;
	}

	public Karajan() {
	}

	void setTemplateGroup(StringTemplateGroup tempGroup) throws IOException {
		m_templates = tempGroup;
	}

	protected StringTemplate template(String name) {
		return m_templates.getInstanceOf(name);
	}

	public StringTemplate program(Program prog) throws Exception {
		VariableScope scope = new VariableScope(this, null);
		scope.bodyTemplate = template("program");

		scope.bodyTemplate.setAttribute("types", prog.getTypes());

		for (int i = 0; i < prog.sizeOfProcedureArray(); i++) {
			Procedure proc = prog.getProcedureArray(i);
			procedure(proc, scope);
		}

		statements(prog, scope);
		return scope.bodyTemplate;
	}

	public void procedure(Procedure proc, VariableScope outerScope) throws Exception {
		StringTemplate procST = template("procedure");
		outerScope.bodyTemplate.setAttribute("procedures", procST);
		procST.setAttribute("name", proc.getName());
		for (int i = 0; i < proc.sizeOfOutputArray(); i++) {
			FormalParameter param = proc.getOutputArray(i);
			StringTemplate paramST = parameter(param);
			procST.setAttribute("outputs", paramST);
			if (!param.isNil())
				procST.setAttribute("optargs", paramST);
			else
				procST.setAttribute("arguments", paramST);
		}
		for (int i = 0; i < proc.sizeOfInputArray(); i++) {
			FormalParameter param = proc.getInputArray(i);
			StringTemplate paramST = parameter(param);
			procST.setAttribute("inputs", paramST);
			if (!param.isNil())
				procST.setAttribute("optargs", paramST);
			else
				procST.setAttribute("arguments", paramST);
		}

		Binding bind;
		if ((bind = proc.getBinding()) != null) {
			binding(bind, procST);
		}
		else {
			VariableScope scope = new VariableScope(this, null);
			scope.bodyTemplate = procST;
			statements(proc, scope);
		}

	}

	public StringTemplate parameter(FormalParameter param) {
		StringTemplate paramST = new StringTemplate("parameter");
		StringTemplate typeST = new StringTemplate("type");
		paramST.setAttribute("name", param.getName());
		typeST.setAttribute("name", param.getType().getLocalPart());
		typeST.setAttribute("namespace", param.getType().getNamespaceURI());
		paramST.setAttribute("type", typeST);
		paramST.setAttribute("isArray", new Boolean(param.getIsArray1()));
		if(!param.isNil())
			paramST.setAttribute("default",expressionToKarajan(param.getAbstractExpression()));
		return paramST;
	}

	public void variable(Variable var, VariableScope scope) throws Exception {
		StringTemplate variableST = template("variable");
		scope.bodyTemplate.setAttribute("declarations", variableST);
		variableST.setAttribute("name", var.getName());
		variableST.setAttribute("type", var.getType().getLocalPart());
		variableST.setAttribute("isArray", Boolean.valueOf(var.getIsArray1()));

		if(!var.isNil()) {
			variableST.setAttribute("expr",expressionToKarajan(var.getAbstractExpression()));
		} else {
			// add temporary mapping info
			StringTemplate mappingST = new StringTemplate("mapping");
			mappingST.setAttribute("descriptor", "concurrent_mapper");
			StringTemplate paramST = template("vdl_parameter");
			paramST.setAttribute("name", "prefix");
			paramST.setAttribute("expr", var.getName() + "-"
					+ UUIDGenerator.getInstance().generateRandomBasedUUID().toString());
			mappingST.setAttribute("params", paramST);
			variableST.setAttribute("mapping", mappingST);
			variableST.setAttribute("nil", Boolean.TRUE);
		}
		scope.addVariable(var.getName());
	}

	public void dataset(Dataset dataset, VariableScope scope) throws Exception {
		StringTemplate datasetST = template("variable");
		scope.bodyTemplate.setAttribute("declarations", datasetST);
		datasetST.setAttribute("name", dataset.getName());
		datasetST.setAttribute("type", dataset.getType().getLocalPart());
		if (dataset.isSetIsArray1()) {
			datasetST.setAttribute("isArray", Boolean.valueOf(dataset.getIsArray1()));
		}
		if (dataset.getFile() != null) {
			StringTemplate fileST = new StringTemplate("file");
			fileST.setAttribute("name", dataset.getFile().getName());
			fileST.defineFormalArgument("params");
			datasetST.setAttribute("file", fileST);
		}
		Mapping mapping = dataset.getMapping();

		if (mapping != null) {
			StringTemplate mappingST = new StringTemplate("mapping");
			mappingST.setAttribute("descriptor", mapping.getDescriptor());
			for (int i = 0; i < mapping.sizeOfParamArray(); i++) {
				Param param = mapping.getParamArray(i);
				StringTemplate paramST = template("vdl_parameter");
				paramST.setAttribute("name", param.getName());
				paramST.setAttribute("expr",expressionToKarajan(param.getAbstractExpression()));
				mappingST.setAttribute("params", paramST);
			}
			datasetST.setAttribute("mapping", mappingST);
		}
		scope.addVariable(dataset.getName());
	}

	public void assign(Assign assign, VariableScope scope) throws Exception {
		StringTemplate assignST = template("assign");
		assignST.setAttribute("var", expressionToKarajan(assign.getAbstractExpressionArray(0)));
		assignST.setAttribute("value", expressionToKarajan(assign.getAbstractExpressionArray(1)));
		String rootvar = abstractExpressionToRootVariable(assign.getAbstractExpressionArray(0));
		scope.addWriter(rootvar, new Integer(callID++));
		scope.appendStatement(assignST);
	}

	public void statements(XmlObject prog, VariableScope scope) throws Exception {
		XmlCursor cursor = prog.newCursor();
		cursor.selectPath("*");

		while (cursor.toNextSelection()) {
			XmlObject child = cursor.getObject();
			statement(child, scope);
		}
	}

	public void statement(XmlObject child, VariableScope scope) throws Exception {
		if (child instanceof Variable) {
			variable((Variable) child, scope);
		}
		else if (child instanceof Dataset) {
			dataset((Dataset) child, scope);
		}
		else if (child instanceof Assign) {
			assign((Assign) child, scope);
		}
		else if (child instanceof Call) {
			call((Call) child, scope);
		}
		else if (child instanceof Foreach) {
			foreachStat((Foreach)child, scope);
		}
		else if (child instanceof Iterate) {
			iterateStat((Iterate)child, scope);
		}
		else if (child instanceof If) {
			ifStat((If)child, scope);
		} else if (child instanceof Switch) {
			switchStat((Switch) child, scope);
		} else if (child instanceof Procedure
			|| child instanceof Types
			|| child instanceof Continue
			|| child instanceof FormalParameter) {
			// ignore these - they're expected but we don't need to
			// do anything for them here
		} else {
			throw new RuntimeException("Unexpected element in XML. Implementing class "+child.getClass()+", content "+child);
		}
	}

	public void call(Call call, VariableScope scope) throws Exception {
		StringTemplate callST = template("call");
		callST.setAttribute("func", call.getProc().getLocalPart());
		StringTemplate parentST = callST.getEnclosingInstance();
		for (int i = 0; i < call.sizeOfInputArray(); i++) {
			ActualParameter input = call.getInputArray(i);
			StringTemplate argST = actualParameter(input);
			callST.setAttribute("inputs", argST);
		}
		for (int i = 0; i < call.sizeOfOutputArray(); i++) {
			ActualParameter output = call.getOutputArray(i);
			StringTemplate argST = actualParameter(output);
			callST.setAttribute("outputs", argST);
			String rootvar = abstractExpressionToRootVariable(call.getOutputArray(i).getAbstractExpression());
			scope.addWriter(rootvar, new Integer(callID++));
		}

		scope.appendStatement(callST);
	}

	public void iterateStat(Iterate iterate, VariableScope scope) throws Exception {
		VariableScope innerScope = new VariableScope(this, scope);
		StringTemplate iterateST = template("iterate");

		XmlObject cond = iterate.getAbstractExpression();
		StringTemplate condST = expressionToKarajan(cond);
		iterateST.setAttribute("cond", condST);
		iterateST.setAttribute("var", iterate.getVar());
		innerScope.addVariable(iterate.getVar());
		innerScope.bodyTemplate = iterateST;

		statements(iterate.getBody(), innerScope);

		Object statementID = new Integer(callID++);
		Iterator scopeIterator = innerScope.getVariableIterator();
		while(scopeIterator.hasNext()) {
			String v=(String) scopeIterator.next();
			scope.addWriter(v, statementID);
		}
		scope.appendStatement(iterateST);
	}

	public void foreachStat(Foreach foreach, VariableScope scope) throws Exception {
		VariableScope innerScope = new VariableScope(this, scope);

		StringTemplate foreachST = template("foreach");
		foreachST.setAttribute("var", foreach.getVar());
		innerScope.addVariable(foreach.getVar());
		foreachST.setAttribute("indexVar", foreach.getIndexVar());
		if(foreach.getIndexVar() !=null) {
			innerScope.addVariable(foreach.getIndexVar());
		}
		XmlObject in = foreach.getIn().getAbstractExpression();
		StringTemplate inST = expressionToKarajan(in);
		foreachST.setAttribute("in", inST);

		innerScope.bodyTemplate = foreachST;

		statements(foreach.getBody(), innerScope);

		Object statementID = new Integer(callID++);
		Iterator scopeIterator = innerScope.getVariableIterator();
		while(scopeIterator.hasNext()) {
			String v=(String) scopeIterator.next();
			scope.addWriter(v, statementID);
		}
		scope.appendStatement(foreachST);

	}

	public void ifStat(If ifstat, VariableScope scope) throws Exception {
		StringTemplate ifST = template("if");
		StringTemplate conditionST = expressionToKarajan(ifstat.getAbstractExpression());
		ifST.setAttribute("condition", conditionST.toString());

		Then thenstat = ifstat.getThen();
		Else elsestat = ifstat.getElse();

		VariableScope innerThenScope = new VariableScope(this, scope);
		innerThenScope.bodyTemplate = template("sub_comp");
		ifST.setAttribute("vthen", innerThenScope.bodyTemplate);

		statements(thenstat, innerThenScope);

		Object statementID = new Integer(callID++);

		Iterator thenScopeIterator = innerThenScope.getVariableIterator();
		while(thenScopeIterator.hasNext()) {
			String v=(String) thenScopeIterator.next();
			scope.addWriter(v, statementID);
		}

		if (elsestat != null) {

			VariableScope innerElseScope = new VariableScope(this, scope);
			innerElseScope.bodyTemplate = template("sub_comp");
			ifST.setAttribute("velse", innerElseScope.bodyTemplate);

			statements(elsestat, innerElseScope);

			Iterator elseScopeIterator = innerElseScope.getVariableIterator();
			while(elseScopeIterator.hasNext()) {
				String v=(String) elseScopeIterator.next();
				scope.addWriter(v, statementID);
			}
		}
		scope.appendStatement(ifST);
	}

	public void switchStat(Switch switchstat, VariableScope scope) throws Exception {
		StringTemplate switchST = template("switch");
		Object statementID = new Integer(callID++);
		scope.bodyTemplate.setAttribute("statements", switchST);
		StringTemplate conditionST = expressionToKarajan(switchstat.getAbstractExpression());
		switchST.setAttribute("condition", conditionST.toString());

		for (int i=0; i< switchstat.sizeOfCaseArray(); i++) {
			Case casestat = switchstat.getCaseArray(i);
			VariableScope caseScope = new VariableScope(this, scope);
			caseScope.bodyTemplate = new StringTemplate("case");
			switchST.setAttribute("cases", caseScope.bodyTemplate);
			
			caseStat(casestat, caseScope);

			Iterator caseScopeIterator = caseScope.getVariableIterator();
			while(caseScopeIterator.hasNext()) {
				String v=(String) caseScopeIterator.next();
				scope.addWriter(v, statementID);
			}

		}
		Default defaultstat = switchstat.getDefault();
		if (defaultstat != null) {
			VariableScope defaultScope = new VariableScope(this, scope);
			defaultScope.bodyTemplate = template("sub_comp");
			switchST.setAttribute("sdefault", defaultScope.bodyTemplate);
			statements(defaultstat, defaultScope);
			Iterator defaultScopeIterator = defaultScope.getVariableIterator();
			while(defaultScopeIterator.hasNext()) {
				String v=(String) defaultScopeIterator.next();
				scope.addWriter(v, statementID);
			}
		}
	}

	public void caseStat(Case casestat, VariableScope scope) throws Exception {
		StringTemplate valueST = expressionToKarajan(casestat.getAbstractExpression());
		scope.bodyTemplate.setAttribute("value", valueST.toString());
		statements(casestat.getStatements(), scope);
	}

	public StringTemplate actualParameter(ActualParameter arg) throws Exception {
		StringTemplate argST = template("call_arg");
		argST.setAttribute("bind", arg.getBind());
		argST.setAttribute("expr", expressionToKarajan(arg.getAbstractExpression()));
		return argST;
	}

	public void binding(Binding bind, StringTemplate procST) throws Exception {
		StringTemplate bindST = new StringTemplate("binding");
		ApplicationBinding app;
		if ((app = bind.getApplication()) != null) {
			bindST.setAttribute("application", application(app));
			procST.setAttribute("binding", bindST);
		} else throw new RuntimeException("Unknown binding: "+bind);
	}

	public StringTemplate application(ApplicationBinding app) throws Exception {
		StringTemplate appST = new StringTemplate("application");
		appST.setAttribute("exec", app.getExecutable());
		for (int i = 0; i < app.sizeOfAbstractExpressionArray(); i++) {
			XmlObject argument = app.getAbstractExpressionArray(i);
			StringTemplate argumentST = expressionToKarajan(argument);
			appST.setAttribute("arguments", argumentST);
		}
		if(app.getStdin()!=null)
			appST.setAttribute("stdin", expressionToKarajan(app.getStdin().getAbstractExpression()));
		if(app.getStdout()!=null)
			appST.setAttribute("stdout", expressionToKarajan(app.getStdout().getAbstractExpression()));
		if(app.getStderr()!=null)
			appST.setAttribute("stderr", expressionToKarajan(app.getStderr().getAbstractExpression()));
		return appST;
	}

	/** Produces a Karajan function invocation from a SwiftScript invocation.
	  * The Karajan invocation will have the same name as the SwiftScript
	  * function, in the 'vdl' Karajan namespace. Parameters to the
	  * Karajan function will differ from the SwiftScript parameters in
	  * a number of ways - read the source for the exact ways in which
	  * that happens.
	  */

	public StringTemplate function(Function func) {
		StringTemplate funcST = template("function");
		funcST.setAttribute("name", func.getName());
		XmlObject[] arguments = func.getAbstractExpressionArray();
		for(int i = 0; i < arguments.length; i++ ) {
			funcST.setAttribute("args",expressionToKarajan(arguments[i]));
		}

		return funcST;
	}

	static final String SWIFTSCRIPT_NS = "http://ci.uchicago.edu/swift/2007/07/swiftscript";

	static final QName OR_EXPR = new QName(SWIFTSCRIPT_NS, "or");
	static final QName AND_EXPR = new QName(SWIFTSCRIPT_NS, "and");
	static final QName BOOL_EXPR = new QName(SWIFTSCRIPT_NS, "booleanConstant");
	static final QName INT_EXPR = new QName(SWIFTSCRIPT_NS, "integerConstant");
	static final QName FLOAT_EXPR = new QName(SWIFTSCRIPT_NS, "floatConstant");
	static final QName STRING_EXPR = new QName(SWIFTSCRIPT_NS, "stringConstant");
	static final QName COND_EXPR = new QName(SWIFTSCRIPT_NS, "cond");
	static final QName ARITH_EXPR = new QName(SWIFTSCRIPT_NS, "arith");
	static final QName UNARY_NEGATION_EXPR = new QName(SWIFTSCRIPT_NS, "unaryNegation");
	static final QName NOT_EXPR = new QName(SWIFTSCRIPT_NS, "not");
	static final QName VARIABLE_REFERENCE_EXPR = new QName(SWIFTSCRIPT_NS, "variableReference");
	static final QName ARRAY_SUBSCRIPT_EXPR = new QName(SWIFTSCRIPT_NS, "arraySubscript");
	static final QName STRUCTURE_MEMBER_EXPR = new QName(SWIFTSCRIPT_NS, "structureMember");
	static final QName ARRAY_EXPR = new QName(SWIFTSCRIPT_NS, "array");
	static final QName RANGE_EXPR = new QName(SWIFTSCRIPT_NS, "range");
	static final QName FUNCTION_EXPR = new QName(SWIFTSCRIPT_NS, "function");

	/** converts an XML intermediate form expression into a
	 *  Karajan expression.
	 */
	public StringTemplate expressionToKarajan(XmlObject expression)
	{
		Node expressionDOM = expression.getDomNode();
		String namespaceURI = expressionDOM.getNamespaceURI();
		String localName = expressionDOM.getLocalName();
		QName expressionQName = new QName(namespaceURI, localName);

		if(expressionQName.equals(OR_EXPR))
		{
			StringTemplate st = template("or");
			BinaryOperator o = (BinaryOperator)expression;
			st.setAttribute("left", expressionToKarajan(o.getAbstractExpressionArray(0)));
			st.setAttribute("right", expressionToKarajan(o.getAbstractExpressionArray(1)));
			return st;
		} else if (expressionQName.equals(AND_EXPR)) {
			StringTemplate st = template("and");
			BinaryOperator o = (BinaryOperator)expression;
			st.setAttribute("left", expressionToKarajan(o.getAbstractExpressionArray(0)));
			st.setAttribute("right", expressionToKarajan(o.getAbstractExpressionArray(1)));
			return st;
		} else if (expressionQName.equals(BOOL_EXPR)) {
			XmlBoolean xmlBoolean = (XmlBoolean) expression;
			boolean b = xmlBoolean.getBooleanValue();
			StringTemplate st = template("bConst");
			st.setAttribute("value",""+b);
			return st;
		} else if (expressionQName.equals(INT_EXPR)) {
			XmlInt xmlInt = (XmlInt) expression;
			int i = xmlInt.getIntValue();
			StringTemplate st = template("iConst");
			st.setAttribute("value",""+i);
			return st;
		} else if (expressionQName.equals(FLOAT_EXPR)) {
			XmlFloat xmlFloat = (XmlFloat) expression;
			float f = xmlFloat.getFloatValue();
			StringTemplate st = template("fConst");
			st.setAttribute("value",""+f);
			return st;
		} else if (expressionQName.equals(STRING_EXPR)) {
			XmlString xmlString = (XmlString) expression;
			String s = xmlString.getStringValue();
			StringTemplate st = template("sConst");
			st.setAttribute("innervalue",s);
			return st;
		} else if (expressionQName.equals(COND_EXPR)) {
			StringTemplate st = template("binaryop");
			LabelledBinaryOperator o = (LabelledBinaryOperator) expression;
			st.setAttribute("op", o.getOp());
			st.setAttribute("left", expressionToKarajan(o.getAbstractExpressionArray(0)));
			st.setAttribute("right", expressionToKarajan(o.getAbstractExpressionArray(1)));
			return st;
		} else if (expressionQName.equals(ARITH_EXPR)) {
			LabelledBinaryOperator o = (LabelledBinaryOperator) expression;
			StringTemplate st = template("binaryop");
			st.setAttribute("op", o.getOp());
			st.setAttribute("left", expressionToKarajan(o.getAbstractExpressionArray(0)));
			st.setAttribute("right", expressionToKarajan(o.getAbstractExpressionArray(1)));
			return st;
		} else if (expressionQName.equals(UNARY_NEGATION_EXPR)) {
			UnlabelledUnaryOperator e = (UnlabelledUnaryOperator) expression;
			StringTemplate st = template("unaryNegation");
			st.setAttribute("exp",expressionToKarajan(e.getAbstractExpression()));
			return st;
		} else if (expressionQName.equals(NOT_EXPR)) {
// TODO not can probably merge with 'unary'
			UnlabelledUnaryOperator e = (UnlabelledUnaryOperator)expression;
			StringTemplate st = template("not");
			st.setAttribute("exp",expressionToKarajan(e.getAbstractExpression()));
			return st;
		} else if (expressionQName.equals(VARIABLE_REFERENCE_EXPR)) {
			XmlString xmlString = (XmlString) expression;
			String s = xmlString.getStringValue();
			StringTemplate st = template("id");
			st.setAttribute("var",s);
			return st;

		} else if (expressionQName.equals(ARRAY_SUBSCRIPT_EXPR)) {
			BinaryOperator op = (BinaryOperator) expression;
			StringTemplate newst = template("extractarrayelement");
			newst.setAttribute("arraychild",expressionToKarajan(op.getAbstractExpressionArray(1)));
			newst.setAttribute("parent",expressionToKarajan(op.getAbstractExpressionArray(0)));
			return newst;
		} else if (expressionQName.equals(STRUCTURE_MEMBER_EXPR)) {
			StructureMember sm = (StructureMember) expression;
			StringTemplate newst = template("extractarrayelement");
			newst.setAttribute("memberchild", sm.getMemberName());
			newst.setAttribute("parent",expressionToKarajan(sm.getAbstractExpression()));
			return newst;
			// TODO the template layout for this and ARRAY_SUBSCRIPT are
			// both a bit convoluted for historical reasons.
			// should be straightforward to tidy up.
		} else if (expressionQName.equals(ARRAY_EXPR)) {
			Array array = (Array)expression;
			StringTemplate st = template("array");
			for (int i = 0; i < array.sizeOfAbstractExpressionArray(); i++) {
				XmlObject expr = array.getAbstractExpressionArray(i);
				st.setAttribute("elements", expressionToKarajan(expr));
			}
			return st;
		} else if (expressionQName.equals(RANGE_EXPR)) {
			Range range = (Range)expression;
			StringTemplate st = template("range");
			st.setAttribute("from", expressionToKarajan(
				range.getAbstractExpressionArray(0)));
			st.setAttribute("to", expressionToKarajan(
				range.getAbstractExpressionArray(1)));
			if(range.sizeOfAbstractExpressionArray()==3) // step is optional
				st.setAttribute("step", expressionToKarajan(
				 range.getAbstractExpressionArray(2)));
			return st;
		} else if (expressionQName.equals(FUNCTION_EXPR)) {
			Function f = (Function) expression;
			StringTemplate st = function(f);
			return st;
		} else {
			throw new RuntimeException("unknown expression implemented by class "+expression.getClass()+" with node name "+expressionQName +" and with content "+expression);
		}
	}

	public String abstractExpressionToRootVariable(XmlObject expression) {
		Node expressionDOM = expression.getDomNode();
		String namespaceURI = expressionDOM.getNamespaceURI();
		String localName = expressionDOM.getLocalName();
		QName expressionQName = new QName(namespaceURI, localName);
		if (expressionQName.equals(VARIABLE_REFERENCE_EXPR)) {
			XmlString xmlString = (XmlString) expression;
			String s = xmlString.getStringValue();
			return s;
		} else if (expressionQName.equals(ARRAY_SUBSCRIPT_EXPR)) {
			BinaryOperator op = (BinaryOperator) expression;
			return abstractExpressionToRootVariable(op.getAbstractExpressionArray(0));
		} else if (expressionQName.equals(STRUCTURE_MEMBER_EXPR)) {
			StructureMember sm = (StructureMember) expression;
			return abstractExpressionToRootVariable(sm.getAbstractExpression());
		} else {
			throw new RuntimeException("Could not find root for abstract expression.");
		}
	}


}
