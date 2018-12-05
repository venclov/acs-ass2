package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.acertainbookstore.business.Book;
import com.acertainbookstore.business.BookCopy;
import com.acertainbookstore.business.SingleLockConcurrentCertainBookStore;
import com.acertainbookstore.business.ImmutableStockBook;
import com.acertainbookstore.business.StockBook;
import com.acertainbookstore.business.TwoLevelLockingConcurrentCertainBookStore;
import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * {@link BookStoreTest} tests the {@link BookStore} interface.
 * 
 * @see BookStore
 */
public class BookStoreTest {

	/** The Constant TEST_ISBN. */
	private static final int TEST_ISBN = 3044560;

	/** The Constant NUM_COPIES. */
	private static final int NUM_COPIES = 5;

	/** The local test. */
	private static boolean localTest = true;

	/** Single lock test */
	private static boolean singleLock = true;

	
	/** The store manager. */
	private static StockManager storeManager;

	/** The client. */
	private static BookStore client;

	/**
	 * Sets the up before class.
	 */
	@BeforeClass
	public static void setUpBeforeClass() {
		try {
			String localTestProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
			localTest = (localTestProperty != null) ? Boolean.parseBoolean(localTestProperty) : localTest;
			
			String singleLockProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_SINGLE_LOCK);
			singleLock = (singleLockProperty != null) ? Boolean.parseBoolean(singleLockProperty) : singleLock;

			if (localTest) {
				if (singleLock) {
					SingleLockConcurrentCertainBookStore store = new SingleLockConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				} else {
					TwoLevelLockingConcurrentCertainBookStore store = new TwoLevelLockingConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				}
			} else {
				storeManager = new StockManagerHTTPProxy("http://localhost:8081/stock");
				client = new BookStoreHTTPProxy("http://localhost:8081");
			}

			storeManager.removeAllBooks();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Helper method to add some books.
	 *
	 * @param isbn
	 *            the isbn
	 * @param copies
	 *            the copies
	 * @throws BookStoreException
	 *             the book store exception
	 */
	public void addBooks(int isbn, int copies) throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		StockBook book = new ImmutableStockBook(isbn, "Test of Thrones", "George RR Testin'", (float) 10, copies, 0, 0,
				0, false);
		booksToAdd.add(book);
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Helper method to get the default book used by initializeBooks.
	 *
	 * @return the default book
	 */
	public StockBook getDefaultBook() {
		return new ImmutableStockBook(TEST_ISBN, "Harry Potter and JUnit", "JK Unit", (float) 10, NUM_COPIES, 0, 0, 0,
				false);
	}

	/**
	 * Method to add a book, executed before every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Before
	public void initializeBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(getDefaultBook());
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Method to clean up the book store, execute after every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@After
	public void cleanupBooks() throws BookStoreException {
		storeManager.removeAllBooks();
	}

	/**
	 * Tests basic buyBook() functionality.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyAllCopiesDefaultBook() throws BookStoreException {
		// Set of books to buy
		Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES));

		// Try to buy books
		client.buyBooks(booksToBuy);

		List<StockBook> listBooks = storeManager.getBooks();
		assertTrue(listBooks.size() == 1);
		StockBook bookInList = listBooks.get(0);
		StockBook addedBook = getDefaultBook();

		assertTrue(bookInList.getISBN() == addedBook.getISBN() && bookInList.getTitle().equals(addedBook.getTitle())
				&& bookInList.getAuthor().equals(addedBook.getAuthor()) && bookInList.getPrice() == addedBook.getPrice()
				&& bookInList.getNumSaleMisses() == addedBook.getNumSaleMisses()
				&& bookInList.getAverageRating() == addedBook.getAverageRating()
				&& bookInList.getNumTimesRated() == addedBook.getNumTimesRated()
				&& bookInList.getTotalRating() == addedBook.getTotalRating()
				&& bookInList.isEditorPick() == addedBook.isEditorPick());
	}

	/**
	 * Tests that books with invalid ISBNs cannot be bought.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyInvalidISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with invalid ISBN.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(-1, 1)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that books can only be bought if they are in the book store.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNonExistingISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with ISBN which does not exist.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(100000, 10)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy more books than there are copies.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyTooManyBooks() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy more copies than there are in store.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES + 1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy a negative number of books.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNegativeNumberOfBookCopies() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a negative number of copies.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that all books can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetBooks() throws BookStoreException {
		Set<StockBook> booksAdded = new HashSet<StockBook>();
		booksAdded.add(getDefaultBook());

		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		booksAdded.addAll(booksToAdd);

		storeManager.addBooks(booksToAdd);

		// Get books in store.
		List<StockBook> listBooks = storeManager.getBooks();

		// Make sure the lists equal each other.
		assertTrue(listBooks.containsAll(booksAdded) && listBooks.size() == booksAdded.size());
	}

	/**
	 * Tests that a list of books with a certain feature can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetCertainBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		storeManager.addBooks(booksToAdd);

		// Get a list of ISBNs to retrieved.
		Set<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN + 1);
		isbnList.add(TEST_ISBN + 2);

		// Get books with that ISBN.
		List<Book> books = client.getBooks(isbnList);

		// Make sure the lists equal each other
		assertTrue(books.containsAll(booksToAdd) && books.size() == booksToAdd.size());
	}

	/**
	 * Tests that books cannot be retrieved if ISBN is invalid.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetInvalidIsbn() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Make an invalid ISBN.
		HashSet<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN); // valid
		isbnList.add(-1); // invalid

		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.getBooks(isbnList);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	@Test
	public void test1() throws BookStoreException {

		int parameter = 5;

		// creating a set of book copies to buy: one copy of default book
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1));

		// creating a set of book copies to add: one copy of default book
		Set<BookCopy> copiesToAdd = new HashSet<>();
		copiesToAdd.add(new BookCopy(TEST_ISBN, 1));


		Thread c1 = new Thread(() -> {
			try {
				for(int i=5; i<parameter; i++) {
					client.buyBooks(booksToBuy);
				}
			} catch (final Throwable t) {
				throw new RuntimeException(t);
			}
		});

		Thread c2 = new Thread(() -> {
			try {
				for(int i=5; i<parameter; i++) {
					storeManager.addCopies(copiesToAdd);
				}
			} catch (final Throwable t) {
				throw new RuntimeException(t);
			}
		});

		c1.start();
		c2.start();
		try {
			c1.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
			fail();
		}

		try {
			c2.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
			fail();
		}


		// Get books in store.
		List<StockBook> listBooks = storeManager.getBooks();

		// Make sure the we have the same number of books
		assertEquals(5,listBooks.get(0).getNumCopies());
	}


	@Test
	public void test2() throws BookStoreException{

		int parameter = 200;

		//remove default books
		storeManager.removeAllBooks();

		//add trylogy to the database
		ImmutableStockBook book_1 = new ImmutableStockBook(3044532,
				"The Lord of the Rings: The Fellowship of the Ring", "J. R. R. Tolkien",
				(float) 10, NUM_COPIES, 0, 0, 0, false);

		ImmutableStockBook book_2 = new ImmutableStockBook(3044533,
				"The Lord of the Rings: The Two Towers", "J. R. R. Tolkien",
				(float) 10, NUM_COPIES, 0, 0, 0, false);

		ImmutableStockBook book_3 = new ImmutableStockBook(3044534,
				"The Lord of the Rings: The Return of the King", "J. R. R. Tolkien",
				(float) 10, NUM_COPIES, 0, 0, 0, false);

		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(book_1);
		booksToAdd.add(book_2);
		booksToAdd.add(book_3);
		storeManager.addBooks(booksToAdd);

		// creating a set of book copies to buy: one copy of each book from trilogy
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(3044532, 1));
		booksToBuy.add(new BookCopy(3044533, 1));
		booksToBuy.add(new BookCopy(3044534, 1));


		// creating a set of book copies to add: one copy of each book from trilogy
		Set<BookCopy> copiesToAdd = new HashSet<>();
		copiesToAdd.add(new BookCopy(3044532, 1));
		copiesToAdd.add(new BookCopy(3044533, 1));
		copiesToAdd.add(new BookCopy(3044534, 1));
		final boolean[] thread_ex = new boolean[0];

		Thread c1 = new Thread(() -> {
			try {
				for(int i=1; i<parameter; i++){
					client.buyBooks(booksToBuy);
					storeManager.addCopies(copiesToAdd);
				}
			} catch (BookStoreException e) {
				e.printStackTrace();
				fail();
			}
		});

		Set<Integer> booksToGet = new HashSet<>();
		booksToGet.add(3044532);
		booksToGet.add(3044533);
		booksToGet.add(3044534);

		c1.start();

		for(int i=1; i<parameter; i++) {
			List<StockBook> listOfBooks = storeManager.getBooksByISBN(booksToGet);

			assertTrue((listOfBooks.get(0).getNumCopies() == NUM_COPIES &&
					listOfBooks.get(1).getNumCopies() == NUM_COPIES &&
					listOfBooks.get(2).getNumCopies() == NUM_COPIES) ||
					(listOfBooks.get(0).getNumCopies() == 4 &&
							listOfBooks.get(0).getNumCopies() == 4 &&
							listOfBooks.get(0).getNumCopies() == 4));
		}


	}

//	@Test
//	public void test3() throws BookStoreException {
//
//	    int parameter = 50;
//
//        // creating a set of book copies to add: one copy of default book
//        Set<BookCopy> copiesToAdd = new HashSet<>();
//        copiesToAdd.add(new BookCopy(TEST_ISBN, 1));
//
//        Thread c1 = new Thread(() -> {
//            try {
//                for(int i=0; i<parameter; i++) {
//                    storeManager.addCopies(copiesToAdd);
//                }
//            } catch (final Throwable t) {
//                throw new RuntimeException(t);
//            }
//        });
//
//        Thread c2 = new Thread(() -> {
//            try {
//                for(int i=0; i<parameter; i++) {
//                    storeManager.addCopies(copiesToAdd);
//                }
//            } catch (final Throwable t) {
//                throw new RuntimeException(t);
//            }
//        });
//
//        c1.start();
//        c2.start();
//        try {
//            c1.join();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        try {
//            c2.join();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//
//        Set<Integer> booksToGet = new HashSet<>();
//        booksToGet.add(TEST_ISBN);
//
//        List<StockBook> listOfBooks = storeManager.getBooksByISBN(booksToGet);
//
//        assertEquals(105,listOfBooks.get(0).getNumCopies());
//
//
//
//	}
//
//	@Test
//	public void test4() throws BookStoreException{
//
//		int parameter = 50;
//
//		// creating a set of book copies to buy: one copy of default book
//		HashSet<BookCopy> booksToBuy_1 = new HashSet<BookCopy>();
//		booksToBuy_1.add(new BookCopy(TEST_ISBN, 3));
//
//		// creating a set of book copies to buy: one copy of default book
//		HashSet<BookCopy> booksToBuy_2 = new HashSet<BookCopy>();
//		booksToBuy_2.add(new BookCopy(TEST_ISBN, 7));
//
//		// creating a set of book copies to add: one copy of default book
//		Set<BookCopy> copiesToAdd_1 = new HashSet<>();
//		copiesToAdd_1.add(new BookCopy(TEST_ISBN, 3));
//
//		// creating a set of book copies to add: one copy of default book
//		Set<BookCopy> copiesToAdd_2 = new HashSet<>();
//		copiesToAdd_2.add(new BookCopy(TEST_ISBN, 7));
//
//		Thread c1 = new Thread(() -> {
//			try {
//				for(int i=0; i<parameter; i++) {
//					storeManager.addCopies(copiesToAdd_1);
//					assertEquals(storeManager.getBooksByISBN().get(0).getNumCopies());
//					client.buyBooks(booksToBuy_1);
//				}
//			} catch (final Throwable t) {
//				throw new RuntimeException(t);
//			}
//		});
//
//		Thread c2 = new Thread(() -> {
//			try {
//				for(int i=0; i<parameter; i++) {
//					storeManager.addCopies(copiesToAdd_1);
//				}
//			} catch (final Throwable t) {
//				throw new RuntimeException(t);
//			}
//		});
//


	/**
	 * Tear down after class.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws BookStoreException {
		storeManager.removeAllBooks();

		if (!localTest) {
			((BookStoreHTTPProxy) client).stop();
			((StockManagerHTTPProxy) storeManager).stop();
		}
	}
}


