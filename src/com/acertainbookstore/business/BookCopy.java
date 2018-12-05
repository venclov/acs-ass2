package com.acertainbookstore.business;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * {@link BookCopy} is used to represent the book and its number of copies.
 */
public class BookCopy {

	private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	/** The ISBN. */
	private int isbn;

	/** The number of copies. */
	private int numCopies;

	/**
	 * Instantiates a new {@link BookCopy} with <code>numCopies</code> book
	 * copies of <code>ISBN</code>.
	 *
	 * @param isbn
	 *            the ISBN
	 * @param numCopies
	 *            the number of copies
	 */
	public BookCopy(int isbn, int numCopies) {
		this.setISBN(isbn);
		this.setNumCopies(numCopies);
	}

	/**
	 * Gets the ISBN of the book.
	 *
	 * @return the ISBN
	 */
	public int getISBN() {
		lock.readLock().lock();
		try {
			return isbn;
		}
		finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Gets the number of book copies.
	 *
	 * @return the number of copies
	 */
	public int getNumCopies() {
		try {
			lock.readLock().lock();
			return numCopies;
		}
		finally {
			lock.readLock().unlock();
		}
	}

	/**
	 * Sets the ISBN of the book.
	 *
	 * @param isbn
	 *            the new ISBN
	 */
	public void setISBN(int isbn) {
		try {
			lock.writeLock().lock();
			this.isbn = isbn;
		}
		finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Sets the number of book copies.
	 *
	 * @param numCopies
	 *            the new number of copies
	 */
	public void setNumCopies(int numCopies) {
		try {
			lock.writeLock().lock();
			this.numCopies = numCopies;
		}
		finally {
			lock.writeLock().unlock();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		lock.readLock().lock();
		try {
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}

			return this.getISBN() == ((BookCopy) obj).getISBN();
		}
		finally {
			lock.readLock().unlock();
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		lock.readLock().lock();
		try {
			return getISBN();
		}
		finally {
			lock.readLock().unlock();
		}
	}
}
