package com.acertainbookstore.business;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This class is a container to manage granularity of the locking protocol
 *
 */
public class LockableBook {
	
	protected BookStoreBook book;
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	
	public LockableBook(BookStoreBook book) {
		this.book = book;
	}
	
	public void writeLock() {
		this.lock.writeLock().lock();
	}
	
	public void unlockWriteLock() {
		this.lock.writeLock().unlock();
	}
	
	public void readLock() {
		this.lock.readLock().lock();
	}
	
	public void unlockReadLock() {
		this.lock.readLock().unlock();
	}
	
	public BookStoreBook getBook() {
		return book;
	}
}
