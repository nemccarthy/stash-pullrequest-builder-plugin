package stashpullrequestbuilder.stashpullrequestbuilder;

import hudson.model.Result;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

/**
 * Created by tariq on 12/04/2017.
 */
public class StashMarkStatusTest {

    private StashRepository repository;

    @Before
    public void setUp() throws Exception {
        repository = mock(StashRepository.class);
    }

    @Test
    public void handleStatus_shouldMarkStatusApprovedOnSuccessfulBuild() throws Exception {
        StashMarkStatus status = new StashMarkStatus();

        status.handleStatus(true, false, "", Result.SUCCESS, repository);

        verify(repository).markStatus("", "APPROVED");
    }

    @Test
    public void handleStatus_shouldMarkStatusNeedsWorkOnFailedBuild() throws Exception {
        StashMarkStatus status = new StashMarkStatus();

        status.handleStatus(false, true, "", Result.FAILURE, repository);

        verify(repository).markStatus("", "NEEDS_WORK");
    }

    @Test
    public void handleStatus_shouldNotMarkStatusApprovedWhenDisabled() throws Exception {
        StashMarkStatus status = new StashMarkStatus();

        status.handleStatus(false, false, "", Result.SUCCESS, repository);

        verify(repository, never()).markStatus("", "APPROVED");
    }

    @Test
    public void handleStatus_shouldNotMarkStatusNeedsWorkWhenDisabled() throws Exception {
        StashMarkStatus status = new StashMarkStatus();

        status.handleStatus(false, false, "", Result.FAILURE, repository);

        verify(repository, never()).markStatus("", "NEEDS_WORK");
    }

}