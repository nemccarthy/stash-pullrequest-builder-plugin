package stashpullrequestbuilder.stashpullrequestbuilder.stash;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * Test for checking the logic inside the StashPullRequestResponseValueRepository class.
 * Since most of this class consists of getters/setters only one method gets tested.
 *
 * @author bart
 */
public class StashPullRequestResponseValueRepositoryTest {

    StashPullRequestResponseValueRepository stashPullRequestResponseValueRepository;

    @Before
    public void setUp() throws Exception {
        stashPullRequestResponseValueRepository =
                new StashPullRequestResponseValueRepository();
    }

    @Test
    public void testSetIdWithNullValue() throws Exception {
        // Test on null values
        stashPullRequestResponseValueRepository.setId(null);
        String branchName = stashPullRequestResponseValueRepository.getBranch().getName();
        assertEquals("", branchName);
    }

    @Test
    public void testSetIdWithEmptyValue() throws Exception {
        // Test on empty String
        stashPullRequestResponseValueRepository.setId("");
        String branchName = stashPullRequestResponseValueRepository.getBranch().getName();
        assertEquals("", branchName);
    }

    @Test
    public void testSetIdWithSlashInBranchName() throws Exception {

        // Test if the branch name get extracted the right way. Allowing for '/' in the branch name
        stashPullRequestResponseValueRepository.setId("refs/heads/release/1.0.0");
        String branchNameWithSlash= stashPullRequestResponseValueRepository.getBranch().getName();
        assertEquals("release/1.0.0", branchNameWithSlash);
    }

    @Test
    public void testSetIdWithNormalBranchName() throws Exception {

        // Normal case, a branch name without '/' characters
        stashPullRequestResponseValueRepository.setId("refs/heads/master");
        String branchNameWithoutSlash = stashPullRequestResponseValueRepository.getBranch().getName();
        assertEquals("master", branchNameWithoutSlash);

    }

    @Test
    public void testSetIdWithUnexpectedBranchName() throws Exception {

        // Normal case, but now with a weird pull request identifier
        stashPullRequestResponseValueRepository.setId("refs/weird/master");
        String branchNameWithoutSlash = stashPullRequestResponseValueRepository.getBranch().getName();
        assertEquals("weird/master", branchNameWithoutSlash);

    }
}