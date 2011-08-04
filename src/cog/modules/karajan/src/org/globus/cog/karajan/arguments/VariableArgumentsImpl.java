// ----------------------------------------------------------------------
// This code is developed as part of the Java CoG Kit project
// The terms of the license can be found at http://www.cogkit.org/license
// This message may not be removed or altered.
// ----------------------------------------------------------------------

/*
 * Created on May 5, 2004
 */
package org.globus.cog.karajan.arguments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import org.globus.cog.karajan.util.KarajanIterator;
import org.globus.cog.karajan.util.ListKarajanIterator;
import org.globus.cog.karajan.util.TypeUtil;

/**
 * Basic implementation of a synchronous channel.
 * 
 * @author Mihael Hategan
 *
 */
public class VariableArgumentsImpl implements VariableArguments {
	public static final Object[] EMPTY_ARRAY = new Object[0];

	private List vargs;

	private static int cid = 0;
	private final int id = cid++;
	
	private final boolean commutative;

	public VariableArgumentsImpl() {
		this(false);
	}
	
	public VariableArgumentsImpl(boolean commutative) {
		this.commutative = commutative;
	}
	
	protected VariableArgumentsImpl(List vargs) {
		this();
		this.vargs = vargs;
	}

	public VariableArgumentsImpl(VariableArguments vargs) {
		this.vargs = new ArrayList(vargs.getAll());
		this.commutative = vargs.isCommutative();
	}

	public synchronized void merge(VariableArguments args) {
		if (vargs == null) {
			vargs = new ArrayList(args.getAll());
		}
		else {
			vargs.addAll(args.getAll());
		}
	}

	public synchronized void append(Object value) {
		if (vargs == null) {
			vargs = new LinkedList();
		}
		vargs.add(value);
	}

	public void appendAll(List args) {
		if (args == null) {
			return;
		}
		if (vargs == null) {
			vargs = new ArrayList();
		}
		vargs.addAll(args);
	}

	public List getAll() {
		if (vargs == null) {
			return Collections.EMPTY_LIST;
		}
		return vargs;
	}

	public void set(List vargs) {
		this.vargs = vargs;
	}

	public Object get(int index) {
		if (vargs == null) {
			throw new IndexOutOfBoundsException(String.valueOf(index));
		}
		return vargs.get(index);
	}
	
	public boolean isEmpty() {
		if (vargs == null) {
			return true;
		}
		return vargs.isEmpty();
	}

	public boolean equals(Object obj) {
		if (obj instanceof VariableArguments) {
			VariableArgumentsImpl other = (VariableArgumentsImpl) obj;
			if ((vargs == null) && (other.vargs != null)) {
				return false;
			}
			if ((vargs != null) && !vargs.equals(other.vargs)) {
				return false;
			}
			return true;
		}
		else {
			return false;
		}
	}

	public int hashCode() {
		if (vargs != null) {
			return vargs.hashCode();
		}
		return getClass().hashCode();
	}
	
	public VariableArguments butFirst() {
		if (vargs != null) {
			return new VariableArgumentsImpl(vargs.subList(1, vargs.size()));
		}
		else {
			throw new NoSuchElementException();
		}
	}

	private static class EmptyIterator implements KarajanIterator {

		public void remove() {
			throw new UnsupportedOperationException();
		}

		public boolean hasNext() {
			return false;
		}

		public Object next() {
			throw new NoSuchElementException();
		}

		public int current() {
			return 0;
		}

		public int count() {
			return 0;
		}

		public Object peek() {
			throw new NoSuchElementException();
		}
	}

	public String toString() {
		if (vargs == null) {
			return "[]";
		}
		else {
			return TypeUtil.listToString(vargs);
		}
	}

	public VariableArguments copy() {
		VariableArguments n = new VariableArgumentsImpl();
		if (this.vargs != null) {
			n.set(new ArrayList(this.vargs));
		}
		return n;
	}

	public int size() {
		int sz = 0;
		if (vargs != null) {
			sz += vargs.size();
		}
		return sz;
	}

	public Iterator iterator() {
		if (vargs == null) {
			return new EmptyIterator();
		}
		else {
			return new ListKarajanIterator(vargs);
		}
	}

	public Object[] toArray() {
		if (vargs != null) {
			return vargs.toArray();
		}
		else {
			return EMPTY_ARRAY;
		}
	}

	public void set(VariableArguments other) {
		this.vargs = other.getAll();
	}

	public Object removeFirst() {
		if (vargs == null) {
			throw new IndexOutOfBoundsException("0");
		}
		return vargs.remove(0);
	}

	public boolean isCommutative() {
		return commutative;
	}
}