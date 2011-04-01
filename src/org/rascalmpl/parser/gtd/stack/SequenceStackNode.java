package org.rascalmpl.parser.gtd.stack;

import org.eclipse.imp.pdb.facts.IConstructor;
import org.rascalmpl.parser.gtd.result.AbstractNode;
import org.rascalmpl.parser.gtd.util.specific.PositionStore;

public class SequenceStackNode extends AbstractStackNode implements IExpandableStackNode{
	private final IConstructor production;
	private final String name;
	
	private final AbstractStackNode[] children;
	
	public SequenceStackNode(int id, int dot, IConstructor production, AbstractStackNode[] children){
		super(id, dot);
		
		this.production = production;
		this.name = String.valueOf(id);
		
		this.children = generateChildren(children);
	}
	
	public SequenceStackNode(int id, int dot, IConstructor production, IMatchableStackNode[] followRestrictions, AbstractStackNode[] children){
		super(id, dot, followRestrictions);
		
		this.production = production;
		this.name = String.valueOf(id);
		
		this.children = generateChildren(children);
	}
	
	public SequenceStackNode(SequenceStackNode original){
		super(original);
		
		production = original.production;
		name = original.name;

		children = original.children;
	}
	
	private AbstractStackNode[] generateChildren(AbstractStackNode[] children){
		AbstractStackNode[] prod = new AbstractStackNode[children.length];
		
		for(int i = children.length - 1; i >= 0; --i){
			AbstractStackNode child = children[i].getCleanCopy();
			child.setProduction(prod);
			prod[i] = child;
		}
		
		AbstractStackNode lastChild = prod[prod.length - 1];
		lastChild.markAsEndNode();
		lastChild.setParentProduction(production);
		
		return new AbstractStackNode[]{prod[0]};
	}
	
	public boolean isEmptyLeafNode(){
		return false;
	}
	
	public String getName(){
		return name;
	}
	
	public void setPositionStore(PositionStore positionStore){
		throw new UnsupportedOperationException();
	}
	
	public boolean match(char[] input){
		throw new UnsupportedOperationException();
	}
	
	public AbstractStackNode getCleanCopy(){
		return new SequenceStackNode(this);
	}
	
	public int getLength(){
		throw new UnsupportedOperationException();
	}
	
	public AbstractStackNode[] getChildren(){
		return children;
	}
	
	public boolean canBeEmpty(){
		return false;
	}
	
	public AbstractStackNode getEmptyChild(){
		throw new UnsupportedOperationException();
	}
	
	public AbstractNode getResult(){
		throw new UnsupportedOperationException();
	}

	public String toString(){
		StringBuilder sb = new StringBuilder();
		sb.append("seq");
		sb.append(name);
		sb.append('(');
		sb.append(startLocation);
		sb.append(')');
		
		return sb.toString();
	}
}
