package org.zorzanotes;

import java.net.URI;
import java.security.SecureRandom;
import java.sql.*;

/** Private aliases: zorza://link/CODE. No external network requests. */
public final class UrlShortener {
    private static final String ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz";
    private static final SecureRandom RANDOM = new SecureRandom();
    public UrlShortener() {
        try (Connection c = Database.connect(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS short_urls (code TEXT PRIMARY KEY, original_url TEXT NOT NULL UNIQUE, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        } catch (SQLException e) { throw new IllegalStateException("Cannot initialize encrypted URL aliases", e); }
    }
    public String shorten(String url) {
        URI uri;
        try { uri = URI.create(url.strip()); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid URL", e); }
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null)
            throw new IllegalArgumentException("Only full http:// or https:// URLs are supported");
        try (Connection c = Database.connect()) {
            try (PreparedStatement p = c.prepareStatement("SELECT code FROM short_urls WHERE original_url=?")) {
                p.setString(1, uri.toString());
                try (ResultSet r = p.executeQuery()) { if (r.next()) return "zorza://link/" + r.getString(1); }
            }
            for (int attempt=0; attempt<10; attempt++) {
                StringBuilder code = new StringBuilder();
                for (int i=0;i<9;i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
                try (PreparedStatement p = c.prepareStatement("INSERT INTO short_urls(code,original_url) VALUES (?,?)")) {
                    p.setString(1,code.toString()); p.setString(2,uri.toString()); p.executeUpdate();
                    return "zorza://link/"+code;
                } catch (SQLException collision) { if (!collision.getMessage().toLowerCase().contains("unique")) throw collision; }
            }
            throw new IllegalStateException("Could not generate a unique alias");
        } catch (SQLException e) { throw new IllegalStateException("Cannot save URL alias",e); }
    }
    public String resolve(String alias) {
        String prefix="zorza://link/";
        if (!alias.startsWith(prefix)) throw new IllegalArgumentException("Not a Zorza URL");
        try (Connection c=Database.connect(); PreparedStatement p=c.prepareStatement("SELECT original_url FROM short_urls WHERE code=?")) {
            p.setString(1,alias.substring(prefix.length()));
            try (ResultSet r=p.executeQuery()) { if(r.next()) return r.getString(1); }
        } catch(SQLException e) { throw new IllegalStateException(e); }
        throw new IllegalArgumentException("Unknown short URL");
    }
}
