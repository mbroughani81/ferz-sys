package io.github.mbroughani81.demo;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

import io.github.mbroughani81.perfspec.IOSpec;

interface DatabaseConnection {
    <T> List<T> query(String sql, Object[] params, Class<T> resultType);
}

interface ResultSet {
    boolean next();

    String getString(String column);

    int getInt(String column);

    long getLong(String column);
}

class Author {
    private long id;
    private String name;
    private String email;

    public Author() {
    }

    public Author(long id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return "Author{id=" + id + ", name='" + name + "', email='" + email + "'}";
    }
}

class Book {
    private long id;
    private String title;
    private String isbn;
    private long authorId;

    public Book() {
    }

    public Book(long id, String title, String isbn, long authorId) {
        this.id = id;
        this.title = title;
        this.isbn = isbn;
        this.authorId = authorId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(long authorId) {
        this.authorId = authorId;
    }

    @Override
    public String toString() {
        return "Book{id=" + id + ", title='" + title + "', isbn='" + isbn + "', authorId=" + authorId + "}";
    }
}

class AuthorWithBooks {
    private Author author;
    private List<Book> books = new ArrayList<>();

    public AuthorWithBooks() {
    }

    public AuthorWithBooks(Author author) {
        this.author = author;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    public List<Book> getBooks() {
        return books;
    }

    public void setBooks(List<Book> books) {
        this.books = books;
    }

    public void addBook(Book book) {
        this.books.add(book);
    }

    @Override
    public String toString() {
        return "AuthorWithBooks{author=" + author + ", books=" + books + "}";
    }
}

class MockDatabaseConnection implements DatabaseConnection {
    private final Map<String, List<?>> storedData;

    public MockDatabaseConnection() {
        this.storedData = new HashMap<>();
        initializeTestData();
    }

    private void initializeTestData() {
        List<Author> authors = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            authors.add(new Author(i, "Author " + i, "author" + i + "@example.com"));
        }
        storedData.put("SELECT * FROM authors", authors);

        List<Book> books = new ArrayList<>();
        long bookId = 1;
        for (int i = 1; i <= 50; i++) {
            int booksPerAuthor = 3 + (i % 3);
            for (int j = 0; j < booksPerAuthor; j++) {
                books.add(new Book(bookId++, "Book " + j + " by Author " + i, "ISBN-" + i + "-" + j, i));
            }
        }
        storedData.put("books_by_author_id", books);
    }

    @Override
    @SuppressWarnings("unchecked")
    @IOSpec(max = 100, unit = TimeUnit.MILLISECONDS, sink = true, percentile = 100, desc = "Single DB Op")
    public <T> List<T> query(String sql, Object[] params, Class<T> resultType) {
        logQuery(sql, params);
        simulateDatabaseLatency();

        if (sql.contains("SELECT * FROM authors") && params.length == 0) {
            return (List<T>) storedData.get("SELECT * FROM authors");
        }

        if (sql.contains("SELECT * FROM books") && params.length == 0) {
            return (List<T>) storedData.get("books_by_author_id");
        }

        if (sql.contains("SELECT * FROM books WHERE author_id = ?")) {
            Long authorId = (Long) params[0];
            List<Book> allBooks = (List<Book>) storedData.get("books_by_author_id");
            List<Book> filteredBooks = new ArrayList<>();
            for (Book book : allBooks) {
                if (book.getAuthorId() == authorId) {
                    filteredBooks.add(book);
                }
            }
            return (List<T>) filteredBooks;
        }

        return new ArrayList<>();
    }

    private void simulateDatabaseLatency() {
        try {
            long delay = 5 + (long) (Math.random() * 10);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void logQuery(String sql, Object[] params) {
        System.out.printf("  [DB Query] SQL: %s | Params: %s%n",
                sql.replaceAll("\\s+", " ").trim(),
                params != null ? Arrays.toString(params) : "[]");
    }
}

class AuthorService {
    private final DatabaseConnection db;

    public AuthorService(DatabaseConnection db) {
        this.db = db;
    }

    @IOSpec(max = 100, unit = TimeUnit.MILLISECONDS, percentile = 100, desc = "Fetching authors with book")
    public List<AuthorWithBooks> getAuthorsWithBooksNPlusOne() {
        System.out.println("\n=== Fetching Authors with Books ===");
        long startTime = System.currentTimeMillis();

        List<AuthorWithBooks> result = new ArrayList<>();

        List<Author> authors = db.query("SELECT * FROM authors", new Object[] {}, Author.class);
        System.out.println("   Authors fetched: " + authors.size());

        for (Author author : authors) {
            db.query(
                    "SELECT * FROM books WHERE author_id = ?",
                    new Object[] { author.getId() },
                    Book.class);
            List<Book> books = db.query(
                    "SELECT * FROM books WHERE author_id = ?",
                    new Object[] { author.getId() },
                    Book.class);

            AuthorWithBooks authorWithBooks = new AuthorWithBooks(author);
            authorWithBooks.setBooks(books);
            result.add(authorWithBooks);
        }

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("=== Completed in " + duration + "ms ===\n");
        return result;
    }

    @IOSpec(max = 100, unit = TimeUnit.MILLISECONDS, percentile = 100, desc = "Fetching authors with book")
    public List<AuthorWithBooks> getAuthorsWithBooksOptimized() {
        System.out.println("\n=== Fetching Authors with Books ===");
        long startTime = System.currentTimeMillis();

        List<Author> allAuthors = db.query("SELECT * FROM authors", new Object[] {}, Author.class);
        List<Book> allBooks = db.query("SELECT * FROM books", new Object[] {}, Book.class);
        System.out.println("   Authors fetched: " + allAuthors.size() + ", Books fetched: " + allBooks.size());

        Map<Long, AuthorWithBooks> authorMap = new HashMap<>();
        for (Author author : allAuthors) {
            authorMap.put(author.getId(), new AuthorWithBooks(author));
        }

        for (Book book : allBooks) {
            AuthorWithBooks awb = authorMap.get(book.getAuthorId());
            if (awb != null) {
                awb.addBook(book);
            }
        }

        List<AuthorWithBooks> result = new ArrayList<>(authorMap.values());

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("=== Completed in " + duration + "ms ===\n");
        return result;
    }
}

public class NPlusOne {

    public static void main(String[] args) {
        System.out.println("=============================================");
        System.out.println("  N+1 Query Problem Demonstration");
        System.out.println("=============================================");

        DatabaseConnection db = new MockDatabaseConnection();
        AuthorService authorService = new AuthorService(db);

        System.out.println("\n📊 Testing: N+1 Query Pattern");
        System.out.println("   Expected: Slow performance, multiple DB calls");
        runPerformanceTest(() -> {
            List<AuthorWithBooks> result = authorService.getAuthorsWithBooksNPlusOne();
            System.out.println("   Retrieved " + result.size() + " authors with their books");
        });

        System.out.println("\n📊 Testing: Optimized Query Pattern");
        System.out.println("   Expected: Fast performance, minimal DB calls");
        runPerformanceTest(() -> {
            List<AuthorWithBooks> result = authorService.getAuthorsWithBooksOptimized();
            System.out.println("   Retrieved " + result.size() + " authors with their books");
        });
        validateIOSpecAnnotations(AuthorService.class);
    }

    private static void runPerformanceTest(Runnable test) {
        long start = System.nanoTime();
        test.run();
        long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        System.out.println("   ⏱️  Total execution time: " + duration + "ms");

    }

    private static void validateIOSpecAnnotations(Class<?> clazz) {
        System.out.println("\nChecking @IOSpec annotations in " + clazz.getSimpleName() + ":");

        for (Method method : clazz.getDeclaredMethods()) {
            IOSpec spec = method.getAnnotation(IOSpec.class);
            if (spec != null) {
                System.out.println("\n  Method: " + method.getName());
                System.out.println("    Max time: " + spec.max() + " " + spec.unit());
                System.out.println("    Percentile: " + spec.percentile() + "th");
                System.out.println("    Description: " + spec.desc());

                // In a real implementation, this would trigger the Pipeline
                System.out.println("    → Would trigger Generator for P-Test Flow creation");
                System.out.println("    → Would feed to LLM for Candidate P-Test generation");
                System.out.println("    → Would be verified by Checker instrumentation");
            }
        }
    }
}
