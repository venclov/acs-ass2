package com.acertainbookstore.business;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.utils.BookStoreUtility;

/** {@link TwoLevelLockingConcurrentCertainBookStore} implements the {@link BookStore} and
 * {@link StockManager} functionalities.
 * 
 * @see BookStore
 * @see StockManager
 */
public class TwoLevelLockingConcurrentCertainBookStore implements BookStore, StockManager {

	/** The mapping of books from ISBN to {@link BookStoreBook}. */
	private Map<Integer, LockableBook> bookMap = null;

	/**
	 *  Lock for the database
	 */
	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

	/**
	 * Instantiates a new {@link CertainBookStore}.
	 */
	public TwoLevelLockingConcurrentCertainBookStore() {
		// Constructors are not synchronized
		bookMap = new HashMap<>();
	}

	private void validate(StockBook book) throws BookStoreException {
		int isbn = book.getISBN();
		String bookTitle = book.getTitle();
		String bookAuthor = book.getAuthor();
		int noCopies = book.getNumCopies();
		float bookPrice = book.getPrice();

		if (BookStoreUtility.isInvalidISBN(isbn)) { // Check if the book has valid ISBN
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isEmpty(bookTitle)) { // Check if the book has valid title
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isEmpty(bookAuthor)) { // Check if the book has valid author
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isInvalidNoCopies(noCopies)) { // Check if the book has at least one copy
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (bookPrice < 0.0) { // Check if the price of the book is valid
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (bookMap.containsKey(isbn)) {// Check if the book is not in stock
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.DUPLICATED);
		}
	}	

	private void validate(BookCopy bookCopy) throws BookStoreException {
		int isbn = bookCopy.getISBN();
		int numCopies = bookCopy.getNumCopies();

		validateISBNInStock(isbn); // Check if the book has valid ISBN and in stock

		if (BookStoreUtility.isInvalidNoCopies(numCopies)) { // Check if the number of the book copy is larger than zero
			throw new BookStoreException(BookStoreConstants.NUM_COPIES + numCopies + BookStoreConstants.INVALID);
		}
	}

	private void validate(BookEditorPick editorPickArg) throws BookStoreException {
		int isbn = editorPickArg.getISBN();
		validateISBNInStock(isbn); // Check if the book has valid ISBN and in stock
	}

	private void validateISBNInStock(Integer ISBN) throws BookStoreException {
		if (BookStoreUtility.isInvalidISBN(ISBN)) { // Check if the book has valid ISBN
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
		}
		if (!bookMap.containsKey(ISBN)) {// Check if the book is in stock
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addBooks(java.util.Set)
	 */
	public void addBooks(Set<StockBook> bookSet) throws BookStoreException {
		if (bookSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		writeLockDB();
		try {
			// Check if all are there
			for (StockBook book : bookSet) {
				validate(book);
			}
			for (StockBook book : bookSet) {
				int isbn = book.getISBN();
				LockableBook loBook = new LockableBook(new BookStoreBook(book));
				bookMap.put(isbn, loBook);
			}	
		} finally {
			unlockWriteLockDB();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addCopies(java.util.Set)
	 */
	public void addCopies(Set<BookCopy> bookCopiesSet) throws BookStoreException {
		int isbn;
		int numCopies;

		readLockDB();
		try {
			if (bookCopiesSet == null) {
				throw new BookStoreException(BookStoreConstants.NULL_INPUT);
			}
			for (BookCopy bookCopy : bookCopiesSet) {
				validate(bookCopy);
			}	

			// acquire all the write lock 
			for(BookCopy bookCopy: bookCopiesSet) {
				isbn = bookCopy.getISBN();
				bookMap.get(isbn).writeLock();
			}
			// Update the number of copies
			try {
				BookStoreBook book;
				for (BookCopy bookCopy : bookCopiesSet) {
					isbn = bookCopy.getISBN();
					numCopies = bookCopy.getNumCopies();
					book = bookMap.get(isbn).getBook();
					book.addCopies(numCopies);
				}	
			} finally {
				// release all the write lock 
				for(BookCopy bookCopy: bookCopiesSet) {
					isbn = bookCopy.getISBN();
					bookMap.get(isbn).unlockWriteLock();
				}				
			}			
		} finally {
			// release database read lock 
			unlockReadLockDB();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooks()
	 */
	public List<StockBook> getBooks() {
		List<StockBook> temp;
		// acquire read lock on the entire database 
		readLockDB();
		try {
			// acquire read lock on every element
			for(LockableBook lBook : bookMap.values()) {
				lBook.readLock();
			}
			// do the operation 
			try {
				Collection<LockableBook> bookMapValues = bookMap.values();

				temp = bookMapValues.stream()
						.map(lb -> lb.getBook().immutableStockBook())
						.collect(Collectors.toList());
			} finally {
				// release all the read locks
				for(LockableBook lBook : bookMap.values()) {
					lBook.unlockReadLock();
				}
			}
		} finally {
			// release the database
			unlockReadLockDB();
		}
		return temp;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#updateEditorPicks(java.util
	 * .Set)
	 */
	public void updateEditorPicks(Set<BookEditorPick> editorPicks) throws BookStoreException {
		// Check that all ISBNs that we add/remove are there first.
		if (editorPicks == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		int isbnValue;
		// read lock on the database
		readLockDB();
		try {
			for (BookEditorPick editorPickArg : editorPicks) {
				validate(editorPickArg);
			}	
			// acquire all the write lock 
			for (BookEditorPick editorPick : editorPicks) {
				isbnValue = editorPick.getISBN();
				bookMap.get(isbnValue).writeLock();
			}
			// try update 
			try {
				for (BookEditorPick editorPickArg : editorPicks) {
					bookMap.get(editorPickArg.getISBN()).getBook().setEditorPick(editorPickArg.isEditorPick());
				}	
			} finally {
				// release all the write locks
				for (BookEditorPick editorPick : editorPicks) {
					isbnValue = editorPick.getISBN();
					bookMap.get(isbnValue).unlockWriteLock();
				}
			}	
		} finally {
			unlockReadLockDB(); 
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#buyBooks(java.util.Set)
	 */
	public void buyBooks(Set<BookCopy> bookCopiesToBuy) throws BookStoreException {
		if (bookCopiesToBuy == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		// Check that all ISBNs that we buy are there first.
		int isbn;
		BookStoreBook book;
		Boolean saleMiss = false;

		Map<Integer, Integer> salesMisses = new HashMap<>();
		// acquire a read lock on the entire database
		readLockDB();
		try {
			// validate
			for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
				isbn = bookCopyToBuy.getISBN(); 

				validate(bookCopyToBuy);

				book = bookMap.get(isbn).getBook();

				if (!book.areCopiesInStore(bookCopyToBuy.getNumCopies())) {
					// If we cannot sell the copies of the book, it is a miss.
					salesMisses.put(isbn, bookCopyToBuy.getNumCopies() - book.getNumCopies());
					saleMiss = true;
				}
			}	
			// acquire the write lock for all the books
			for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
				isbn = bookCopyToBuy.getISBN();
				bookMap.get(isbn).writeLock();
			}
			// try the operation
			try {
				// We throw exception now since we want to see how many books in the
				// order incurred misses which is used by books in demand
				if (saleMiss) {
					for (Map.Entry<Integer, Integer> saleMissEntry : salesMisses.entrySet()) {
						book = bookMap.get(saleMissEntry.getKey()).getBook();
						book.addSaleMiss(saleMissEntry.getValue());
					}
					throw new BookStoreException(BookStoreConstants.BOOK + BookStoreConstants.NOT_AVAILABLE);
				}

				// Then make the purchase.
				for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
					book = bookMap.get(bookCopyToBuy.getISBN()).getBook();
					book.buyCopies(bookCopyToBuy.getNumCopies());
				}	
			} finally {
				for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
					isbn = bookCopyToBuy.getISBN();
					bookMap.get(isbn).unlockWriteLock();
				}
			}	
		} finally {
			// unlock the database
			unlockReadLockDB();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#getBooksByISBN(java.util.
	 * Set)
	 */
	public List<StockBook> getBooksByISBN(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		// read lock on the database
		List<StockBook> temp;
		readLockDB();
		try {
			for (Integer ISBN : isbnSet) {
				validateISBNInStock(ISBN);
			}

			// acquire all the read locks
			for(Integer ISBN: isbnSet) {
				bookMap.get(ISBN).readLock();
			}
			// try the operation
			try {
				temp = isbnSet.stream()
						.map(isbn -> bookMap.get(isbn).getBook().immutableStockBook())
						.collect(Collectors.toList());	
			} finally {
				// unlocks all the read locks
				for(Integer ISBN: isbnSet) {
					bookMap.get(ISBN).unlockReadLock();
				}				
			}	
		} finally {
			// release the database
			unlockReadLockDB();
		}
		return temp;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getBooks(java.util.Set)
	 */
	public List<Book> getBooks(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		List<Book> temp;
		// read lock on the database
		readLockDB();
		try {
			// Check that all ISBNs that we rate are there to start with.
			for (Integer ISBN : isbnSet) {
				validateISBNInStock(ISBN);
			}
			// acquire all the read locks 
			for (Integer ISBN : isbnSet) {
				bookMap.get(ISBN).readLock();
			}
			try {
				temp = isbnSet.stream()
						.map(isbn -> bookMap.get(isbn).getBook().immutableBook())
						.collect(Collectors.toList());		
			} finally {
				// release all the read locks 
				for (Integer ISBN : isbnSet) {
					bookMap.get(ISBN).unlockReadLock();
				}
			}
		} finally {
			unlockReadLockDB();
		}
		return temp;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getEditorPicks(int)
	 */
	public List<Book> getEditorPicks(int numBooks) throws BookStoreException {
		if (numBooks < 0) {
			throw new BookStoreException("numBooks = " + numBooks + ", but it must be positive");
		}
		List<Book> temp;
		// read lock on the DB
		readLockDB();
		try {
			// lock all read book
			for (LockableBook lb : bookMap.values()) {
				lb.readLock();
			}
			try {
				List<LockableBook> listAllEditorPicks = bookMap.entrySet().stream()
						.map(pair -> pair.getValue())
						.filter(lb -> lb.getBook().isEditorPick())
						.collect(Collectors.toList());

				// Find numBooks random indices of books that will be picked.
				Random rand = new Random();
				Set<Integer> tobePicked = new HashSet<>();
				int rangePicks = listAllEditorPicks.size();

				if (rangePicks <= numBooks) {

					// We need to add all books.
					for (int i = 0; i < listAllEditorPicks.size(); i++) {
						tobePicked.add(i);
					}
				} else {

					// We need to pick randomly the books that need to be returned.
					int randNum;

					while (tobePicked.size() < numBooks) {
						randNum = rand.nextInt(rangePicks);
						tobePicked.add(randNum);
					}
				}

				// Return all the books by the randomly chosen indices.
				temp = tobePicked.stream()
						.map(index -> listAllEditorPicks.get(index).getBook().immutableBook())
						.collect(Collectors.toList());
			} finally {
				// release all the read locks
				for (LockableBook lb : bookMap.values()) {
					lb.unlockReadLock();
				}
			}	
		} finally {
			unlockReadLockDB();
		}
		return temp;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#getTopRatedBooks(int)
	 */
	@Override
	public List<Book> getTopRatedBooks(int numBooks) throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#getBooksInDemand()
	 */
	@Override
	public List<StockBook> getBooksInDemand() throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.BookStore#rateBooks(java.util.Set)
	 */
	@Override
	public void rateBooks(Set<BookRating> bookRating) throws BookStoreException {
		throw new BookStoreException();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.acertainbookstore.interfaces.StockManager#removeAllBooks()
	 */
	public void removeAllBooks() throws BookStoreException {
		// acquire a write lock on the entire database
		writeLockDB();
		try {
			bookMap.clear();	
		} finally {
			unlockWriteLockDB();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#removeBooks(java.util.Set)
	 */
	public void removeBooks(Set<Integer> isbnSet) throws BookStoreException {
		// acquire write lock on the entire database
		writeLockDB();
		try {
			if (isbnSet == null) {
				throw new BookStoreException(BookStoreConstants.NULL_INPUT);
			}

			for (Integer ISBN : isbnSet) {
				if (BookStoreUtility.isInvalidISBN(ISBN)) {
					throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
				}

				if (!bookMap.containsKey(ISBN)) {
					throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
				}
			}

			for (int isbn : isbnSet) {
				bookMap.remove(isbn);
			}	
		} finally {
			unlockWriteLockDB();
		}
	}

	/**
	 * Support methods to lock and unlock the database
	 */
	private void writeLockDB() {
		this.lock.writeLock().lock();
	}

	private void unlockWriteLockDB() {
		this.lock.writeLock().unlock();
	}

	private void readLockDB() {
		this.lock.readLock().lock();
	}

	private void unlockReadLockDB() {
		this.lock.readLock().unlock();
	}
}