package org.rascalmpl.library.experiments.Compiler.RVM.Interpreter;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.rascalmpl.debug.IRascalMonitor;
import org.rascalmpl.interpreter.types.FunctionType;
import org.rascalmpl.interpreter.types.RascalTypeFactory;
import org.rascalmpl.value.IAnnotatable;
import org.rascalmpl.value.IConstructor;
import org.rascalmpl.value.IExternalValue;
import org.rascalmpl.value.IValue;
import org.rascalmpl.value.IWithKeywordParameters;
import org.rascalmpl.value.exceptions.IllegalOperationException;
import org.rascalmpl.value.impl.AbstractExternalValue;
import org.rascalmpl.value.type.Type;
import org.rascalmpl.value.type.TypeFactory;
import org.rascalmpl.value.visitors.IValueVisitor;

public class OverloadedFunctionInstance implements ICallableCompiledValue, IExternalValue {
	
	final int[] functions;
	private final int[] constructors;
	final Frame env;
	
	private Type type;
	private Function[] functionStore;
	private List<Type> constructorStore;
	
	final RVMCore rvm;
	
	public OverloadedFunctionInstance(final int[] functions, final int[] constructors, final Frame env, 
									  final Function[] functionStore, final List<Type> constructorStore, final RVMCore rvm) {
		this.functions = functions;
		this.constructors = constructors;
		this.env = env;
		this.functionStore = functionStore;
		this.constructorStore = constructorStore;
		this.rvm = rvm;
	}
	
	public int[] getFunctions() {
		return functions;
	}

	int[] getConstructors() {
		return constructors;
	}
	
	int getArity(){
		if(functions.length > 0){
			return functionStore[functions[0]].nformals;
		}
		if(constructors.length > 0){
			return constructorStore.get(constructors[0]).getArity();
		}
		throw new RuntimeException("Cannot get arity without functions and constructors");
	}

	public String toString(){
		StringBuilder sb = new StringBuilder("OverloadedFunctionInstance[");
		if(getFunctions().length > 0){
			sb.append("functions:");
			for(int i = 0; i < getFunctions().length; i++){
				int fi = getFunctions()[i];
				sb.append(" ").append(functionStore[fi].getName()).append("/").append(fi);
			}
		}
		if(getConstructors().length > 0){
			if(getFunctions().length > 0){
				sb.append("; ");
			}
			sb.append("constructors:");
			for(int i = 0; i < getConstructors().length; i++){
				int ci = getConstructors()[i];
				sb.append(" ").append(constructorStore.get(ci).getName()).append("/").append(ci);
			}
		}
		sb.append("]");
		return sb.toString();
	}
	
	/**
	 * Assumption: scopeIn != -1  
	 */
	public static OverloadedFunctionInstance computeOverloadedFunctionInstance(final int[] functions, final int[] constructors, final Frame cf, final int scopeIn,
			                                                                   final Function[] functionStore, final List<Type> constructorStore, final RVMCore rvm) {
		for(Frame env = cf; env != null; env = env.previousScope) {
			if (env.scopeId == scopeIn) {
				return new OverloadedFunctionInstance(functions, constructors, env, functionStore, constructorStore, rvm);
			}
		}
		throw new CompilerError("Could not find a matching scope when computing a nested overloaded function instance: " + scopeIn, rvm.getStdErr(), cf);
	}

	@Override
	public Type getType() {
		// TODO: this information should probably be available statically?
		if(this.type != null) {
			return this.type;
		}
		Set<FunctionType> types = new HashSet<FunctionType>();
		for(int fun : this.getFunctions()) {
			types.add((FunctionType) functionStore[fun].ftype);
		}
		for(int constr : this.getConstructors()) {
			Type type = constructorStore.get(constr);
			// TODO: void type for the keyword parameters is not right. They should be retrievable from a type store dynamically.
			types.add((FunctionType) RascalTypeFactory.getInstance().functionType(type.getAbstractDataType(), type.getFieldTypes(), TypeFactory.getInstance().voidType()));
		}
		this.type = RascalTypeFactory.getInstance().overloadedFunctionType(types);
		return this.type;
	}

	@Override
	public <T, E extends Throwable> T accept(IValueVisitor<T, E> v) throws E {
		return v.visitExternal((IExternalValue) this);
	}

	@Override
	public boolean isEqual(IValue other) {
		return this == other;
	}

	@Override
	public boolean isAnnotatable() {
		return false;
	}

	@Override
	public IAnnotatable<? extends IValue> asAnnotatable() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean mayHaveKeywordParameters() {
		return false;
	}

	@Override
	public IWithKeywordParameters<? extends IValue> asWithKeywordParameters() {
		throw new IllegalOperationException("Cannot be viewed as with keyword parameters", getType());
	}

	@Override
	public IConstructor encodeAsConstructor() {
		return AbstractExternalValue.encodeAsConstructor(this);
	}
  
	@Override
	public IValue call(IRascalMonitor monitor, Type[] argTypes, IValue[] argValues,	Map<String, IValue> keyArgValues) {
		return rvm.executeRVMFunction(this, argValues, keyArgValues);
	}

	@Override
	public IValue call(Type[] argTypes, IValue[] argValues, Map<String, IValue> keyArgValues) {
		return call(rvm.getMonitor(), argTypes, argValues, keyArgValues);
	}
}
