package org.opensingular.dbuserprovider;

import lombok.extern.jbosslog.JBossLog;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputUpdater;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.keycloak.storage.user.UserRegistrationProvider;
import org.opensingular.dbuserprovider.model.QueryConfigurations;
import org.opensingular.dbuserprovider.model.UserAdapter;
import org.opensingular.dbuserprovider.persistence.DataSourceProvider;
import org.opensingular.dbuserprovider.persistence.UserRepository;
import org.opensingular.dbuserprovider.util.PagingUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * User Storage Provider adapted for Keycloak 26.
 *
 * Changes from previous version:
 * - {@link UserLookupProvider}: since Keycloak 19, methods were renamed to
 *   getUserById(RealmModel, String), getUserByUsername(RealmModel, String) and
 *   getUserByEmail(RealmModel, String), with realm as the first parameter.
 * - {@link UserQueryProvider}: getUsers and several overloads were removed;
 *   search now returns Stream and the consolidated API is searchForUserStream(RealmModel, Map, ...).
 * - {@code getDisableableCredentialTypes} returns {@code Stream<String>}.
 */
@JBossLog
public class DBUserStorageProvider implements UserStorageProvider,
                                              UserLookupProvider,
                                              UserQueryProvider,
                                              CredentialInputUpdater,
                                              CredentialInputValidator,
                                              UserRegistrationProvider {

    private final KeycloakSession session;
    private final ComponentModel  model;
    private final UserRepository  repository;
    private final boolean         allowDatabaseToOverwriteKeycloak;

    DBUserStorageProvider(KeycloakSession session, ComponentModel model, DataSourceProvider dataSourceProvider, QueryConfigurations queryConfigurations) {
        this.session    = session;
        this.model      = model;
        this.repository = new UserRepository(dataSourceProvider, queryConfigurations);
        this.allowDatabaseToOverwriteKeycloak = queryConfigurations.getAllowDatabaseToOverwriteKeycloak();
    }


    private Stream<UserModel> toUserModel(RealmModel realm, List<Map<String, String>> users) {
        if (users == null) {
            return Stream.empty();
        }
        return users.stream()
                    .map(m -> (UserModel) new UserAdapter(session, realm, model, m, allowDatabaseToOverwriteKeycloak));
    }


    // ------------------------------------------------------------------
    // CredentialInputValidator
    // ------------------------------------------------------------------

    @Override
    public boolean supportsCredentialType(String credentialType) {
        return PasswordCredentialModel.TYPE.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        log.infov("isValid user credential: userId={0}", user.getId());

        if (!supportsCredentialType(input.getType())) {
            return false;
        }

        String challengeResponse = input.getChallengeResponse();
        if (challengeResponse == null) {
            return false;
        }

        // In Keycloak 26, CachedUserModel was moved to an internal module (keycloak-model-infinispan)
        // not accessible to external providers via Maven. Cache invalidation when
        // allowDatabaseToOverwriteKeycloak is active is managed by Keycloak itself.
        // Here we only validate the credential directly against the repository.
        return repository.validateCredentials(user.getUsername(), challengeResponse);
    }

    // ------------------------------------------------------------------
    // CredentialInputUpdater
    // ------------------------------------------------------------------

    @Override
    public boolean updateCredential(RealmModel realm, UserModel user, CredentialInput input) {
        log.infov("updating credential: realm={0} user={1}", realm.getId(), user.getUsername());

        if (!supportsCredentialType(input.getType()) || !(input instanceof UserCredentialModel)) {
            return false;
        }

        UserCredentialModel cred = (UserCredentialModel) input;
        return repository.updateCredentials(user.getUsername(), cred.getChallengeResponse());
    }

    @Override
    public void disableCredentialType(RealmModel realm, UserModel user, String credentialType) {
    }

    @Override
    public Stream<String> getDisableableCredentialTypesStream(RealmModel realm, UserModel user) {
        return Stream.empty();
    }

    // ------------------------------------------------------------------
    // UserStorageProvider
    // ------------------------------------------------------------------

    @Override
    public void preRemove(RealmModel realm) {
        log.infov("pre-remove realm");
    }

    @Override
    public void preRemove(RealmModel realm, GroupModel group) {
        log.infov("pre-remove group");
    }

    @Override
    public void preRemove(RealmModel realm, RoleModel role) {
        log.infov("pre-remove role");
    }

    @Override
    public void close() {
        log.debugv("closing");
    }

    // ------------------------------------------------------------------
    // UserLookupProvider (Keycloak 19+)
    // ------------------------------------------------------------------

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        log.infov("lookup user by id: realm={0} userId={1}", realm.getId(), id);

        String externalId = StorageId.externalId(id);
        Map<String, String> user = repository.findUserById(externalId);

        if (user == null) {
            log.debugv("findUserById returned null, skipping creation of UserAdapter, expect login error");
            return null;
        }
        return new UserAdapter(session, realm, model, user, allowDatabaseToOverwriteKeycloak);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        log.infov("lookup user by username: realm={0} username={1}", realm.getId(), username);

        return repository.findUserByUsername(username)
                .map(u -> (UserModel) new UserAdapter(session, realm, model, u, allowDatabaseToOverwriteKeycloak))
                .orElse(null);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        log.infov("lookup user by email: realm={0} email={1}", realm.getId(), email);
        return getUserByUsername(realm, email);
    }

    // ------------------------------------------------------------------
    // UserQueryProvider (Keycloak 19+) 
    // ------------------------------------------------------------------

    @Override
    public int getUsersCount(RealmModel realm) {
        return repository.getUsersCount(null);
    }

    @Override
    public int getUsersCount(RealmModel realm, String search) {
        return repository.getUsersCount(search);
    }

    @Override
    public int getUsersCount(RealmModel realm, Map<String, String> params) {
        return repository.getUsersCount(extractSearch(params));
    }

    @Override
    public int getUsersCount(RealmModel realm, String search, Set<String> groupIds) {
        return repository.getUsersCount(search);
    }

    @Override
    public int getUsersCount(RealmModel realm, Map<String, String> params, Set<String> groupIds) {
        return repository.getUsersCount(extractSearch(params));
    }

    @Override
    public int getUsersCount(RealmModel realm, boolean includeServiceAccount) {
        return repository.getUsersCount(null);
    }

    /**
     * User search for Keycloak 19+. {@code params} may contain:
     * - "keycloak.session.realm.users.query.search" (UserModel.SEARCH) → search term
     * - "username", "email", "firstName", "lastName" → specific filters
     *
     * Since our provider uses a single parameterized query, we merge all these
     * values into a single search term.
     */
    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, Map<String, String> params, Integer firstResult, Integer maxResults) {
        log.infov("search for users with params: realm={0} params={1} firstResult={2} maxResults={3}",
                realm.getId(), params, firstResult, maxResults);

        String search = extractSearch(params);
        PagingUtil.Pageable pageable = toPageable(firstResult, maxResults);
        return toUserModel(realm, repository.findUsers(search, pageable));
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm, String attrName, String attrValue) {
        log.infov("search for users by attribute: realm={0} attrName={1} attrValue={2}", realm.getId(), attrName, attrValue);
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realm, GroupModel group, Integer firstResult, Integer maxResults) {
        log.infov("get group members: realm={0} groupId={1} firstResult={2} maxResults={3}",
                realm.getId(), group.getId(), firstResult, maxResults);
        return Stream.empty();
    }

    // ------------------------------------------------------------------
    // UserRegistrationProvider
    // ------------------------------------------------------------------

    @Override
    public UserModel addUser(RealmModel realm, String username) {
        // According to the docs: "If your provider has a configuration switch to turn off
        // adding a user, returning null from this method will skip the provider and call the next one."
        return null;
    }

    @Override
    public boolean removeUser(RealmModel realm, UserModel user) {
        boolean userRemoved = repository.removeUser();
        if (userRemoved) {
            log.infov("deleted keycloak user: realm={0} userId={1} username={2}",
                    realm.getId(), user.getId(), user.getUsername());
        }
        return userRemoved;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static PagingUtil.Pageable toPageable(Integer firstResult, Integer maxResults) {
        if (firstResult == null && maxResults == null) {
            return null;
        }
        int first = firstResult == null ? 0 : firstResult;
        int max = maxResults == null ? Integer.MAX_VALUE : maxResults;
        if (max <= 0) {
            return null;
        }
        return new PagingUtil.Pageable(first, max);
    }

    /**
     * Extracts a search term from the parameter map passed by Keycloak.
     *
     * Priority:
     *   1. UserModel.SEARCH ("keycloak.session.realm.users.query.search")
     *   2. "search"
     *   3. username
     *   4. email
     *   5. firstName
     *   6. lastName
     *   7. any first value
     */
    private static String extractSearch(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        String search = params.get(UserModel.SEARCH);
        if (search != null && !search.isEmpty()) {
            return search;
        }
        search = params.get("search");
        if (search != null && !search.isEmpty()) {
            return search;
        }
        for (String key : new String[]{UserModel.USERNAME, UserModel.EMAIL, UserModel.FIRST_NAME, UserModel.LAST_NAME}) {
            String v = params.get(key);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return params.values().stream().filter(Objects::nonNull).findFirst().orElse(null);
    }
}
