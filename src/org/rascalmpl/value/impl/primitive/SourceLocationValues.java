/*******************************************************************************
 * Copyright (c) 2012-2013 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *
 *   * Arnold Lankamp - implementation
 *   * Jurgen Vinju - implementation
 *   * Michael Steindorfer - Michael.Steindorfer@cwi.nl - CWI
 *******************************************************************************/
package org.rascalmpl.value.impl.primitive;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import org.rascalmpl.value.ISourceLocation;
import org.rascalmpl.value.IValue;
import org.rascalmpl.value.impl.AbstractValue;
import org.rascalmpl.value.type.Type;
import org.rascalmpl.value.type.TypeFactory;
import org.rascalmpl.value.visitors.IValueVisitor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * This is a container class for a number of implementations of ISourceLocation. Each implementation is extremely similar to the others.
 * except that different native types are used to store offsets, lengths, line and column indices. The goal is to use a minimum amount
 * of heap for each source location object, since at run-time there will be so many of them. We measured the effect of this on some real 
 * applications and showed more than 50% improvement in memory usage.
 */
/*package*/ class SourceLocationValues {
	
	
	
	/*package*/ static ISourceLocation newSourceLocation(ISourceLocation loc, int offset, int length) {
		ISourceLocation root = loc.top();
		if (offset < 0) throw new IllegalArgumentException("offset should be positive");
		if (length < 0) throw new IllegalArgumentException("length should be positive");

		if (offset < Byte.MAX_VALUE && length < Byte.MAX_VALUE) {
			return new SourceLocationValues.ByteByte(root, (byte) offset, (byte) length);
		}

		if (offset < Character.MAX_VALUE && length < Character.MAX_VALUE) {
			return new SourceLocationValues.CharChar(root, (char) offset, (char) length);
		}

		return new SourceLocationValues.IntInt(root, offset, length);
	}
	
	/*package*/ static ISourceLocation newSourceLocation(ISourceLocation loc, int offset, int length, int beginLine, int endLine, int beginCol, int endCol) {
		ISourceLocation root = loc.top();
		if (offset < 0) throw new IllegalArgumentException("offset should be positive");
		if (length < 0) throw new IllegalArgumentException("length should be positive");
		if (beginLine < 0) throw new IllegalArgumentException("beginLine should be positive");
		if (beginCol < 0) throw new IllegalArgumentException("beginCol should be positive");
		if (endCol < 0) throw new IllegalArgumentException("endCol should be positive");
		if (endLine < beginLine)
			throw new IllegalArgumentException("endLine should be larger than or equal to beginLine");
		if (endLine == beginLine && endCol < beginCol)
			throw new IllegalArgumentException("endCol should be larger than or equal to beginCol, if on the same line");

		if (offset < Character.MAX_VALUE
				&& length < Character.MAX_VALUE
				&& beginLine < Byte.MAX_VALUE
				&& endLine < Byte.MAX_VALUE
				&& beginCol < Byte.MAX_VALUE
				&& endCol < Byte.MAX_VALUE) {
			return new SourceLocationValues.CharCharByteByteByteByte(root, (char) offset, (char) length, (byte) beginLine, (byte) endLine, (byte) beginCol, (byte) endCol);
		} else if (offset < Character.MAX_VALUE
				&& length < Character.MAX_VALUE
				&& beginLine < Character.MAX_VALUE
				&& endLine < Character.MAX_VALUE
				&& beginCol < Character.MAX_VALUE
				&& endCol < Character.MAX_VALUE) {
			return new SourceLocationValues.CharCharCharCharCharChar(root, (char) offset, (char) length, (char) beginLine, (char) endLine, (char) beginCol, (char) endCol);
		} else if (beginLine < Character.MAX_VALUE
				&& endLine < Character.MAX_VALUE
				&& beginCol < Byte.MAX_VALUE
				&& endCol < Byte.MAX_VALUE) {
			return new SourceLocationValues.IntIntCharCharByteByte(root, offset, length, (char) beginLine, (char) endLine, (byte) beginCol, (byte) endCol);
		} else if (beginCol < Byte.MAX_VALUE
				&& endCol < Byte.MAX_VALUE) {
			return new SourceLocationValues.IntIntIntIntByteByte(root, offset, length, beginLine, endLine, (byte) beginCol, (byte) endCol);
		}

		return new SourceLocationValues.IntIntIntIntIntInt(root, offset, length, beginLine, endLine, beginCol, endCol);
	}	
	
    private final static Cache<URI, ISourceLocation> locationCache = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();
        
	private final static Cache<ISourceLocation,URI>  reverseLocationCache = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();
	
	/*package*/ static ISourceLocation newSourceLocation(URI uri) throws URISyntaxException {
		if (uri.isOpaque()) {
			throw new UnsupportedOperationException("Opaque URI schemes are not supported; the scheme-specific part must start with a / character.");
		}
		
		try {
            return locationCache.get(uri, u -> {
                try {
                    return newSourceLocation(u.getScheme(), u.getAuthority(), u.getPath(), u.getQuery(), u.getFragment());
                } 
                catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof URISyntaxException) {
                throw (URISyntaxException)e.getCause();
            }
            throw e;
        }
	}
	
	/*package*/ static ISourceLocation newSourceLocation(String scheme, String authority,
			String path, String query, String fragment) throws URISyntaxException {
		return SourceLocationURIValues.newURI(scheme, authority, path, query, fragment);
	}

	
	private abstract static class Complete extends Incomplete {
		private Complete(ISourceLocation root) {
			super(root);
		}

		@Override
		public boolean hasOffsetLength() {
			return true;
		}
		
		@Override
		public boolean hasLineColumn() {
			return true;
		}
	}
	
	
	private abstract static class Incomplete extends AbstractValue implements ISourceLocation {
		protected ISourceLocation root;

		public Incomplete(ISourceLocation root) {
			this.root = root;
		}
		
		@Override
		public Boolean hasAuthority() {
			return root.hasAuthority();
		}
		
		@Override
		public Boolean hasFragment() {
			return root.hasFragment();
		}
		
		@Override
		public Boolean hasPath() {
			return root.hasPath();
		}
		
		@Override
		public Boolean hasQuery() {
			return root.hasQuery();
		}
		
		@Override
		public String getAuthority() {
			return root.getAuthority();
		}
		
		@Override
		public String getFragment() {
			return root.getFragment();
		}
		
		@Override
		public String getPath() {
			return root.getPath();
		}
		
		@Override
		public String getQuery() {
			return root.getQuery();
		}
		
		@Override
		public String getScheme() {
			return root.getScheme();
		}
		
		@Override
		public ISourceLocation top() {
			return root;
		}
		
		@Override
		public URI getURI() {
		    return reverseLocationCache.get(root, u -> {
                URI result = u.getURI();
                try {
                    // assure correct encoding, side effect of JRE's implementation of URIs
                    result = new URI(result.toASCIIString());
                } catch (URISyntaxException e) {
                } 
                return result;
		    });
		}
		
		@Override
		public Type getType(){
			return TypeFactory.getInstance().sourceLocationType();
		}
		
		@Override
		public boolean hasLineColumn() {
			return false;
		}
		
		@Override
		public boolean hasOffsetLength() {
			return false;
		}
		
		@Override
		public int getBeginColumn() throws UnsupportedOperationException {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public int getBeginLine() throws UnsupportedOperationException {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public int getEndColumn() throws UnsupportedOperationException {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public int getEndLine() throws UnsupportedOperationException {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public int getLength() throws UnsupportedOperationException {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public int getOffset() throws UnsupportedOperationException {
			throw new UnsupportedOperationException();
		}
		
		@Override
		public <T, E extends Throwable> T accept(IValueVisitor<T,E> v) throws E{
	    	return v.visitSourceLocation(this);
		}
		
		@Override
		public boolean isEqual(IValue value){
			return equals(value);
		}
	}
	
	private static class IntIntIntIntIntInt extends Complete {
		protected final int offset;
		protected final int length;
		protected final int beginLine;
		protected final int endLine;
		protected final int beginCol;
		protected final int endCol;
		
		private IntIntIntIntIntInt(ISourceLocation root, int offset, int length, int beginLine, int endLine, int beginCol, int endCol){
			super(root);
			
			this.offset = offset;
			this.length = length;
			this.beginLine = beginLine;
			this.endLine = endLine;
			this.beginCol = beginCol;
			this.endCol = endCol;
		}
		@Override
		public Type getType(){
			return TypeFactory.getInstance().sourceLocationType();
		}
		
		@Override
		public int getBeginLine(){
			return beginLine;
		}
		
		@Override
		public int getEndLine(){
			return endLine;
		}
		
		@Override
		public int getBeginColumn(){
			return beginCol;
		}
		
		@Override
		public int getEndColumn(){
			return endCol;
		}
		
		@Override
		public int getOffset(){
			return offset;
		}
		
		@Override
		public int getLength(){
			return length;
		}
		
		@Override
		public int hashCode(){
			int hash = root.hashCode();
			hash ^= beginLine << 3;
			hash ^= (endLine << 23);
			hash ^= (beginCol << 13);
			hash ^= (endCol << 18);
			hash ^= (offset << 8);
			hash ^= (length << 29);
			
			return hash;
		}
		
		@Override
		public boolean equals(Object o){
			if(o == null) return false;
			
			if(o.getClass() == getClass()){
				IntIntIntIntIntInt otherSourceLocation = (IntIntIntIntIntInt) o;
				return (root.equals(otherSourceLocation.root)
						&& (beginLine == otherSourceLocation.beginLine)
						&& (endLine == otherSourceLocation.endLine)
						&& (beginCol == otherSourceLocation.beginCol)
						&& (endCol == otherSourceLocation.endCol)
						&& (offset == otherSourceLocation.offset)
						&& (length == otherSourceLocation.length));
			}
			
			return false;
		}
	}
	
	private static class CharCharByteByteByteByte extends Complete {
		protected final char offset;
		protected final char length;
		protected final byte beginLine;
		protected final byte endLine;
		protected final byte beginCol;
		protected final byte endCol;

		private CharCharByteByteByteByte(ISourceLocation root, char offset, char length, byte beginLine, byte endLine, byte beginCol, byte endCol){
			super(root);
			
			this.offset = offset;
			this.length = length;
			this.beginLine = beginLine;
			this.endLine = endLine;
			this.beginCol = beginCol;
			this.endCol = endCol;
		}
		@Override
		public Type getType(){
			return TypeFactory.getInstance().sourceLocationType();
		}
		
		@Override
		public int getBeginLine(){
			return beginLine;
		}
		
		@Override
		public int getEndLine(){
			return endLine;
		}
		
		@Override
		public int getBeginColumn(){
			return beginCol;
		}
		
		@Override
		public int getEndColumn(){
			return endCol;
		}
		
		@Override
		public int getOffset(){
			return offset;
		}
		
		@Override
		public int getLength(){
			return length;
		}
		
		@Override
		public int hashCode(){
			int hash = root.hashCode();
			hash ^= beginLine << 3;
			hash ^= (endLine << 23);
			hash ^= (beginCol << 13);
			hash ^= (endCol << 18);
			hash ^= (offset << 8);
			hash ^= (length << 29);
			
			return hash;
		}
		
		@Override
		public boolean equals(Object o){
			if(o == null) return false;
			
			if(o.getClass() == getClass()){
				CharCharByteByteByteByte otherSourceLocation = (CharCharByteByteByteByte) o;
				return (root.equals(otherSourceLocation.root)
						&& (beginLine == otherSourceLocation.beginLine)
						&& (endLine == otherSourceLocation.endLine)
						&& (beginCol == otherSourceLocation.beginCol)
						&& (endCol == otherSourceLocation.endCol)
						&& (offset == otherSourceLocation.offset)
						&& (length == otherSourceLocation.length));
			}
			
			return false;
		}
	}
	
	private static class CharCharCharCharCharChar extends Complete {
		protected final char offset;
		protected final char length;
		protected final char beginLine;
		protected final char endLine;
		protected final char beginCol;
		protected final char endCol;
		
		private CharCharCharCharCharChar(ISourceLocation root, char offset, char length, char beginLine, char endLine, char beginCol, char endCol){
			super(root);
			
			this.offset = offset;
			this.length = length;
			this.beginLine = beginLine;
			this.endLine = endLine;
			this.beginCol = beginCol;
			this.endCol = endCol;
		}
		@Override
		public Type getType(){
			return TypeFactory.getInstance().sourceLocationType();
		}
		
		@Override
		public int getBeginLine(){
			return beginLine;
		}
		
		@Override
		public int getEndLine(){
			return endLine;
		}
		
		@Override
		public int getBeginColumn(){
			return beginCol;
		}
		
		@Override
		public int getEndColumn(){
			return endCol;
		}
		
		@Override
		public int getOffset(){
			return offset;
		}
		
		@Override
		public int getLength(){
			return length;
		}
		
		@Override
		public int hashCode(){
			int hash = root.hashCode();
			hash ^= beginLine << 3;
			hash ^= (endLine << 23);
			hash ^= (beginCol << 13);
			hash ^= (endCol << 18);
			hash ^= (offset << 8);
			hash ^= (length << 29);
			
			return hash;
		}
		
		@Override
		public boolean equals(Object o){
			if(o == null) return false;
			
			if(o.getClass() == getClass()){
				CharCharCharCharCharChar otherSourceLocation = (CharCharCharCharCharChar) o;
				return (root.equals(otherSourceLocation.root)
						&& (beginLine == otherSourceLocation.beginLine)
						&& (endLine == otherSourceLocation.endLine)
						&& (beginCol == otherSourceLocation.beginCol)
						&& (endCol == otherSourceLocation.endCol)
						&& (offset == otherSourceLocation.offset)
						&& (length == otherSourceLocation.length));
			}
			
			return false;
		}
	}

	private static class IntIntIntIntByteByte extends Complete {
		protected final int offset;
		protected final int length;
		protected final int beginLine;
		protected final int endLine;
		protected final byte beginCol;
		protected final byte endCol;
		
		private IntIntIntIntByteByte(ISourceLocation root, int offset, int length, int beginLine, int endLine, byte beginCol, byte endCol){
			super(root);
			
			this.offset = offset;
			this.length = length;
			this.beginLine = beginLine;
			this.endLine = endLine;
			this.beginCol = beginCol;
			this.endCol = endCol;
		}

		@Override
		public int getBeginLine(){
			return beginLine;
		}
		
		@Override
		public int getEndLine(){
			return endLine;
		}
		
		@Override
		public int getBeginColumn(){
			return beginCol;
		}
		
		@Override
		public int getEndColumn(){
			return endCol;
		}
		
		@Override
		public int getOffset(){
			return offset;
		}
		
		@Override
		public int getLength(){
			return length;
		}
		
		@Override
		public int hashCode(){
			int hash = root.hashCode();
			hash ^= beginLine << 3;
			hash ^= (endLine << 23);
			hash ^= (beginCol << 13);
			hash ^= (endCol << 18);
			hash ^= (offset << 8);
			hash ^= (length << 29);
			
			return hash;
		}
		
		@Override
		public boolean equals(Object o){
			if(o == null) return false;
			
			if(o.getClass() == getClass()){
				IntIntIntIntByteByte otherSourceLocation = (IntIntIntIntByteByte) o;
				return (root.equals(otherSourceLocation.root)
						&& (beginLine == otherSourceLocation.beginLine)
						&& (endLine == otherSourceLocation.endLine)
						&& (beginCol == otherSourceLocation.beginCol)
						&& (endCol == otherSourceLocation.endCol)
						&& (offset == otherSourceLocation.offset)
						&& (length == otherSourceLocation.length));
			}
			
			return false;
		}
	}

	private static class IntIntCharCharByteByte extends Complete {
		protected final int offset;
		protected final int length;
		protected final char beginLine;
		protected final char endLine;
		protected final byte beginCol;
		protected final byte endCol;
		
		private IntIntCharCharByteByte(ISourceLocation root, int offset, int length, char beginLine, char endLine, byte beginCol, byte endCol){
			super(root);
			
			this.offset = offset;
			this.length = length;
			this.beginLine = beginLine;
			this.endLine = endLine;
			this.beginCol = beginCol;
			this.endCol = endCol;
		}

		@Override
		public int getBeginLine(){
			return beginLine;
		}
		
		@Override
		public int getEndLine(){
			return endLine;
		}
		
		@Override
		public int getBeginColumn(){
			return beginCol;
		}
		
		@Override
		public int getEndColumn(){
			return endCol;
		}
		
		@Override
		public int getOffset(){
			return offset;
		}
		
		@Override
		public int getLength(){
			return length;
		}
		
		@Override
		public int hashCode(){
			int hash = root.hashCode();
			hash ^= beginLine << 3;
			hash ^= (endLine << 23);
			hash ^= (beginCol << 13);
			hash ^= (endCol << 18);
			hash ^= (offset << 8);
			hash ^= (length << 29);
			
			return hash;
		}
		
		@Override
		public boolean equals(Object o){
			if(o == null) return false;
			
			if(o.getClass() == getClass()){
				IntIntCharCharByteByte otherSourceLocation = (IntIntCharCharByteByte) o;
				return (root.equals(otherSourceLocation.root)
						&& (beginLine == otherSourceLocation.beginLine)
						&& (endLine == otherSourceLocation.endLine)
						&& (beginCol == otherSourceLocation.beginCol)
						&& (endCol == otherSourceLocation.endCol)
						&& (offset == otherSourceLocation.offset)
						&& (length == otherSourceLocation.length));
			}
			
			return false;
		}
	}

	private static class ByteByte extends Incomplete {
		protected final byte offset;
		protected final byte length;
		
		private ByteByte(ISourceLocation root, byte offset, byte length){
			super(root);
			
			this.offset = offset;
			this.length = length;
		}
		
		@Override
		public boolean hasOffsetLength() {
			return true;
		}
		
		@Override
		public int getOffset(){
			return offset;
		}
		
		@Override
		public int getLength(){
			return length;
		}
		
		@Override
		public int hashCode(){
			int hash = root.hashCode();
			hash ^= (offset << 8);
			hash ^= (length << 29);
			
			return hash;
		}
		
		@Override
		public boolean equals(Object o){
			if(o == null) return false;
			
			if(o.getClass() == getClass()){
				ByteByte otherSourceLocation = (ByteByte) o;
				return (root.equals(otherSourceLocation.root)
						&& (offset == otherSourceLocation.offset)
						&& (length == otherSourceLocation.length));
			}
			
			return false;
		}
	}

	private static class CharChar extends Incomplete {
		protected final char offset;
		protected final char length;
		
		private CharChar(ISourceLocation root, char offset, char length){
			super(root);
			
			this.offset = offset;
			this.length = length;
		}
		
		@Override
		public boolean hasOffsetLength() {
			return true;
		}
		
		@Override
		public int getOffset(){
			return offset;
		}
		
		@Override
		public int getLength(){
			return length;
		}
		
		@Override
		public int hashCode(){
			int hash = root.hashCode();
			hash ^= (offset << 8);
			hash ^= (length << 29);
			
			return hash;
		}
		
		@Override
		public boolean equals(Object o){
			if(o == null) return false;
			
			if(o.getClass() == getClass()){
				CharChar otherSourceLocation = (CharChar) o;
				return (root.equals(otherSourceLocation.root)
						&& (offset == otherSourceLocation.offset)
						&& (length == otherSourceLocation.length));
			}
			
			return false;
		}
	}
	
	private static class IntInt extends Incomplete {
		protected final int offset;
		protected final int length;
		
		private IntInt(ISourceLocation root, int offset, int length){
			super(root);
			
			this.offset = offset;
			this.length = length;
		}
		
		@Override
		public boolean hasOffsetLength() {
			return true;
		}
		
		@Override
		public boolean hasLineColumn() {
			return false;
		}

		@Override
		public int getOffset(){
			return offset;
		}
		
		@Override
		public int getLength(){
			return length;
		}
		
		@Override
		public int hashCode(){
			int hash = root.hashCode();
			hash ^= (offset << 8);
			hash ^= (length << 29);
			
			return hash;
		}
		
		@Override
		public boolean equals(Object o){
			if(o == null) return false;
			
			if(o.getClass() == getClass()){
				IntInt otherSourceLocation = (IntInt) o;
				return (root.equals(otherSourceLocation.root)
						&& (offset == otherSourceLocation.offset)
						&& (length == otherSourceLocation.length));
			}
			
			return false;
		}
	}


}
