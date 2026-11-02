package ru.bank.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bank.dao.ContactDao;
import ru.bank.dao.ContactDao.Kind;
import ru.bank.dao.UserDao;
import ru.bank.domain.Models.Contact;
import java.util.List;
import java.util.Locale;

@Service
public class ContactService {
    private final UserDao users;
    private final ContactDao contacts;
    public ContactService(UserDao users, ContactDao contacts) { this.users = users; this.contacts = contacts; }
    public static String normalizeEmail(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private void lock(long userId) { if (!users.lock(userId)) { throw BusinessException.notFound(); } }

    @Transactional
    public Contact add(long user, Kind kind, String value) {
        lock(user);
        return contacts.insert(user, kind, normalize(kind, value));
    }
    @Transactional
    public Contact update(long user, Kind kind, long id, String value) {
        lock(user);
        String normalized = normalize(kind, value);
        if (!contacts.update(user, kind, id, normalized)) { throw BusinessException.notFound(); }
        return new Contact(id, normalized);
    }
    @Transactional
    public void delete(long user, Kind kind, long id) {
        lock(user);
        List<Contact> existing = contacts.list(user, kind);
        if (existing.stream().noneMatch(contact -> contact.id == id)) { throw BusinessException.notFound(); }
        if (existing.size() <= 1) { throw BusinessException.conflict("LAST_CONTACT", "At least one email and one phone must remain"); }
        contacts.delete(user, kind, id);
    }
    private String normalize(Kind kind, String value) { return kind == Kind.EMAIL ? normalizeEmail(value) : value; }
}
