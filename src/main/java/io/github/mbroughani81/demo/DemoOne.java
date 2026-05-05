package io.github.mbroughani81.demo;

import java.util.*;
import java.util.concurrent.TimeUnit;
import io.github.mbroughani81.perfspec.MSpec;

/* -------------------- core abstractions -------------------- */

interface DataAccessProvider {
    <T> List<T> query(String sql, Object[] params, Class<T> resultType);
}

class Author {
    private long id;
    private String name;
    private String email;

    public Author() {}

    public Author(long id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}

class Book {
    private long id;
    private String title;
    private String isbn;
    private long authorId;

    public Book() {}

    public Book(long id, String title, String isbn, long authorId) {
        this.id = id;
        this.title = title;
        this.isbn = isbn;
        this.authorId = authorId;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public long getAuthorId() { return authorId; }
    public void setAuthorId(long authorId) { this.authorId = authorId; }
}

class AuthorWithBooks {
    private Author author;
    private List<Book> books = new ArrayList<>();

    public AuthorWithBooks() {}

    public AuthorWithBooks(Author author) {
        this.author = author;
    }

    public Author getAuthor() { return author; }
    public void setAuthor(Author author) { this.author = author; }
    public List<Book> getBooks() { return books; }
    public void setBooks(List<Book> books) { this.books = books; }
    public void addBook(Book book) { this.books.add(book); }
}

/* -------------------- in-memory data provider -------------------- */

class MemoryBasedDataProvider implements DataAccessProvider {
    private final List<Author> authors = new ArrayList<>();
    private final List<Book> books = new ArrayList<>();

    public MemoryBasedDataProvider() {
    }

    public void addAuthor(Author author) {
        authors.add(author);
    }

    public void addBook(Book book) {
        books.add(book);
    }

    @Override
    @SuppressWarnings("unchecked")
    @MSpec(max = 10, unit = TimeUnit.MILLISECONDS, sink = true, percentile = 100, desc = "Data access")
    public <T> List<T> query(String sql, Object[] params, Class<T> resultType) {
        String trimmedSql = sql.trim().toLowerCase();

        if (trimmedSql.equals("select * from author")) {
            return (List<T>) authors;
        }
        if (trimmedSql.equals("select * from book")) {
            return (List<T>) books;
        }
        if (trimmedSql.startsWith("select * from book where author_id")) {
            if (params != null && params.length == 1 && params[0] instanceof Long) {
                long authorId = (Long) params[0];
                List<Book> filtered = new ArrayList<>();
                for (Book b : books) {
                    if (b.getAuthorId() == authorId) {
                        filtered.add(b);
                    }
                }
                return (List<T>) filtered;
            }
        }
        return new ArrayList<>();
    }
}

/* -------------------- business service -------------------- */

class EntityService {
    private final DataAccessProvider provider;

    public EntityService(DataAccessProvider provider) {
        this.provider = provider;
    }

    @MSpec(max = 100, unit = TimeUnit.MILLISECONDS, percentile = 100, desc = "Aggregate details")
    public List<AuthorWithBooks> retrieveAggregated() {
        List<Author> allAuthors = provider.query("SELECT * FROM author", new Object[]{}, Author.class);
        List<AuthorWithBooks> result = new ArrayList<>();

        for (Author a : allAuthors) {
            List<Book> relatedBooks = provider.query("SELECT * FROM book WHERE author_id = ?", new Object[]{a.getId()}, Book.class);
            AuthorWithBooks combined = new AuthorWithBooks(a);
            combined.setBooks(relatedBooks);
            result.add(combined);
        }
        return result;
    }

    @MSpec(max = 100, unit = TimeUnit.MILLISECONDS, percentile = 100, desc = "Aggregate details (optimised)")
    public List<AuthorWithBooks> retrieveAggregatedBulk() {
        List<Author> allAuthors = provider.query("SELECT * FROM author", new Object[]{}, Author.class);
        List<Book> allBooks = provider.query("SELECT * FROM book", new Object[]{}, Book.class);

        Map<Long, AuthorWithBooks> map = new HashMap<>();
        for (Author a : allAuthors) {
            map.put(a.getId(), new AuthorWithBooks(a));
        }
        for (Book b : allBooks) {
            AuthorWithBooks container = map.get(b.getAuthorId());
            if (container != null) {
                container.addBook(b);
            }
        }
        return new ArrayList<>(map.values());
    }
}

/* -------------------- entry point -------------------- */

public class DemoOne {
    public static void main(String[] args) {
        System.out.println("Hello World");
    }
}
