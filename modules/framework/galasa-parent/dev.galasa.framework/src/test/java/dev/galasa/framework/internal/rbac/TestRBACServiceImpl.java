/*
 * Copyright contributors to the Galasa project
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package dev.galasa.framework.internal.rbac;

import java.time.Instant;
import java.util.Iterator;
import java.util.Map;

import org.junit.*;

import dev.galasa.framework.mocks.MockAuthStoreService;
import dev.galasa.framework.mocks.MockEnvironment;
import dev.galasa.framework.mocks.MockIDynamicStatusStoreService;
import dev.galasa.framework.mocks.MockTimeService;
import dev.galasa.framework.spi.rbac.Action;
import dev.galasa.framework.spi.rbac.RBACService;
import dev.galasa.framework.spi.rbac.Role;

import static org.assertj.core.api.Assertions.*;

public class TestRBACServiceImpl {
    
    @Test
    public void testRolesMapByIdContainsAdminRole() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService , new MockEnvironment());
        Map<String,Role> roleMap = service.getRolesMapById();

        Role roleGot = roleMap.get("2");
        assertThat(roleGot).isNotNull();
        assertThat(roleGot.getName()).isEqualTo("admin");
        assertThat(roleGot.getDescription()).contains("Administrator access");

        assertThat(roleGot.getActionIds())
            .hasSize(9)
            .contains("USER_ROLE_UPDATE_ANY")
            .contains("SECRETS_GET_UNREDACTED_VALUES")
            .contains("GENERAL_API_ACCESS")
            .contains("CPS_PROPERTIES_DELETE")
            .contains("CPS_PROPERTIES_SET")
            .contains("SECRETS_SET")
            .contains("SECRETS_DELETE")
            .contains("USER_DELETE_OTHER")
            .contains("RUNS_DELETE_OTHER_USERS");
    }

    @Test
    public void testRolesMapByIdContainsTesterRole() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Map<String,Role> roleMap = service.getRolesMapById();

        Role roleGot = roleMap.get("1");
        assertThat(roleGot).isNotNull();
        assertThat(roleGot.getName()).isEqualTo("tester");

        assertThat(roleGot.getActionIds())
            .hasSize(2)
            .contains("USER_ROLE_UPDATE_ANY")
            .contains("GENERAL_API_ACCESS");
    }

    @Test
    public void testRolesMapByIdContainsDeactivateddRole() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Map<String,Role> roleMap = service.getRolesMapById();

        Role roleGot = roleMap.get("0");
        assertThat(roleGot).isNotNull();
        assertThat(roleGot.getName()).isEqualTo("deactivated");

        assertThat(roleGot.getActionIds())
        .hasSize(0);
    }

    @Test 
    public void testActionsMapByIdContainsActionUserRoleUpdateAny() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Map<String,Action> actionMap = service.getActionsMapById();

        Action action = actionMap.get("USER_ROLE_UPDATE_ANY");
        assertThat(action.getId()).isEqualTo("USER_ROLE_UPDATE_ANY");
    }


    @Test 
    public void testActionsMapByIdContainsActionSecretsGet() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Map<String,Action> actionMap = service.getActionsMapById();

        Action action = actionMap.get("SECRETS_GET_UNREDACTED_VALUES");
        assertThat(action.getId()).isEqualTo("SECRETS_GET_UNREDACTED_VALUES");
    }

    @Test 
    public void testActionsMapByIdContainsActionSecretsSet() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Map<String,Action> actionMap = service.getActionsMapById();

        Action action = actionMap.get("SECRETS_SET");
        assertThat(action.getId()).isEqualTo("SECRETS_SET");
    }

    @Test 
    public void testActionsMapByIdContainsActionCpsPropertiesDelete() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Map<String,Action> actionMap = service.getActionsMapById();

        Action action = actionMap.get("CPS_PROPERTIES_DELETE");
        assertThat(action.getId()).isEqualTo("CPS_PROPERTIES_DELETE");
    }

    @Test 
    public void testActionsMapByIdContainsActionSecretsDelete() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Map<String,Action> actionMap = service.getActionsMapById();

        Action action = actionMap.get("SECRETS_DELETE");
        assertThat(action.getId()).isEqualTo("SECRETS_DELETE");
    }

    @Test 
    public void testActionsMapByIdContainsActionRunsDeleteOtherUsers() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Map<String,Action> actionMap = service.getActionsMapById();

        Action action = actionMap.get("RUNS_DELETE_OTHER_USERS");
        assertThat(action.getId()).isEqualTo("RUNS_DELETE_OTHER_USERS");
    }

    @Test 
    public void testActionsMapByIdContainsActionGeneralApiAccess() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Map<String,Action> actionMap = service.getActionsMapById();

        Action action = actionMap.get("GENERAL_API_ACCESS");
        assertThat(action.getId()).isEqualTo("GENERAL_API_ACCESS");
    }

    @Test 
    public void testActionsMapByIdContainsActionCpsPropertiesSet() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment() );
        Map<String,Action> actionMap = service.getActionsMapById();

        Action action = actionMap.get("CPS_PROPERTIES_SET");
        assertThat(action.getId()).isEqualTo("CPS_PROPERTIES_SET");
    }

    @Test
    public void testActionsMapByNameContainsSecretsGet() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Map<String,Action> actionMapById = service.getActionsMapById();

        Action action = actionMapById.get("SECRETS_GET_UNREDACTED_VALUES");

        assertThat(action.getId()).isEqualTo("SECRETS_GET_UNREDACTED_VALUES");
    }

    @Test
    public void testServiceCanLookupAdminRoleById() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Role roleGotBack = service.getRoleById("2");
        assertThat(roleGotBack.getName()).isEqualTo("admin");
    }

    @Test
    public void testServiceCanLookupGetSecretsActionById() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Action actionGotBack = service.getActionById("SECRETS_GET_UNREDACTED_VALUES");
        assertThat(actionGotBack.getId()).isEqualTo("SECRETS_GET_UNREDACTED_VALUES");
    }

    @Test
    public void testServiceCanLookupGetSecretsActionByName() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Action actionGotBack = service.getActionById("SECRETS_GET_UNREDACTED_VALUES");
        assertThat(actionGotBack.getId()).isEqualTo("SECRETS_GET_UNREDACTED_VALUES");
    }

    @Test
    public void testServiceCanLookupSetSecretsActionById() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Action actionGotBack = service.getActionById("SECRETS_SET");
        assertThat(actionGotBack.getId()).isEqualTo("SECRETS_SET");
    }

    @Test
    public void testServiceCanLookupDeletePropertiesActionById() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Action actionGotBack = service.getActionById("CPS_PROPERTIES_DELETE");
        assertThat(actionGotBack.getId()).isEqualTo("CPS_PROPERTIES_DELETE");
    }

    @Test
    public void testServiceCanLookupRunsDeleteOtherUsersActionById() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Action actionGotBack = service.getActionById("RUNS_DELETE_OTHER_USERS");
        assertThat(actionGotBack.getId()).isEqualTo("RUNS_DELETE_OTHER_USERS");
    }

    @Test
    public void testServiceCanLookupDeleteSecretsActionById() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Action actionGotBack = service.getActionById("SECRETS_DELETE");
        assertThat(actionGotBack.getId()).isEqualTo("SECRETS_DELETE");
    }

    @Test
    public void testGetSecretsActionHasDescription() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Action actionGotBack = service.getActionById("SECRETS_GET_UNREDACTED_VALUES");
        assertThat(actionGotBack.getDescription()).contains("Able to get unredacted secret values");
    }

    @Test 
    public void testSetCpsPropertiesActionHasDescription() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Map<String,Action> actionMap = service.getActionsMapById();

        Action action = actionMap.get("CPS_PROPERTIES_SET");
        assertThat(action.getDescription()).isEqualTo("Able to set CPS properties");
    }

    @Test
    public void testActionsAreSorted() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Iterator<Action> walker = service.getActionsSortedByName().iterator();
        assertThat(walker.hasNext()).isTrue();
        // Only check the first one. Should be enough...
        assertThat(walker.next().getId()).isEqualTo("CPS_PROPERTIES_DELETE");
    }

    @Test
    public void testRolesAreSorted() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        Iterator<Role> walker = service.getRolesSortedByName().iterator();
        assertThat(walker.hasNext()).isTrue();
        // Only check the first one (alphabetically). Should be enough...
        assertThat(walker.next().getName()).isEqualTo("admin");
    }

    @Test
    public void testDefaultRoleIsDeactivated() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();

        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, new MockEnvironment());
        String defaultRoleId = service.getDefaultRoleId();
        Role defaultRole = service.getRoleById(defaultRoleId);
        assertThat(defaultRole).isNotNull();
        assertThat(defaultRole.getName()).isEqualTo("deactivated");
    }
    @Test
    public void testDefaultRoleIsSetByEnvVariable() throws Exception {
        MockTimeService mockTimeService = new MockTimeService(Instant.now());
        MockAuthStoreService mockAuthStoreService = new MockAuthStoreService(mockTimeService);
        MockIDynamicStatusStoreService mockDssService = new MockIDynamicStatusStoreService();
        MockEnvironment env = new MockEnvironment();
        env.setenv("GALASA_DEFAULT_USER_ROLE", "tester");
        RBACService service = new RBACServiceImpl(mockDssService, mockAuthStoreService, env);
        String defaultRoleId = service.getDefaultRoleId();
        Role defaultRole = service.getRoleById(defaultRoleId);
        assertThat(defaultRole).isNotNull();
        assertThat(defaultRole.getName()).isEqualTo("tester");
    }
}
