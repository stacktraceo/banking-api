package ru.bank.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.bank.api.ApiModels.*;
import ru.bank.dao.AccountDao;
import ru.bank.dao.ContactDao;
import ru.bank.dao.ContactDao.Kind;
import ru.bank.dao.UserDao;
import ru.bank.domain.Models.User;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserDao users;
    private final ContactDao contacts;
    private final AccountDao accounts;
    public UserService(UserDao users, ContactDao contacts, AccountDao accounts) { this.users = users; this.contacts = contacts; this.accounts = accounts; }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public UserResponse get(long id) { return response(users.find(id).orElseThrow(BusinessException::notFound)); }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public MeResponse me(long id) { return new MeResponse(get(id), accounts.balance(id)); }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public UserPage search(LocalDate date, String phone, String name, String email, int page, int size) {
        String normalized = email == null ? null : ContactService.normalizeEmail(email);
        List<UserResponse> content = users.search(date, phone, name, normalized, page, size).stream().map(this::response).collect(Collectors.toList());
        return new UserPage(content, users.count(date, phone, name, normalized), page, size);
    }
    private UserResponse response(User user) { return new UserResponse(user, contacts.list(user.id, Kind.EMAIL), contacts.list(user.id, Kind.PHONE)); }
}
