Stash Pull Request Builder Plugin
================================

> ## This plugin is now maintained in the offical Jenkins org; https://github.com/jenkinsci/stash-pullrequest-builder-plugin Please direct all PRs there.
> ## Issues can be raised here; https://issues.jenkins.io/browse/JENKINS-63802?jql=project%20%3D%20JENKINS%20AND%20component%20%3D%20stash-pullrequest-builder-plugin

[![Join the chat at https://gitter.im/nemccarthy/stash-pullrequest-builder-plugin](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/nemccarthy/stash-pullrequest-builder-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This Jenkins plugin builds pull requests from a Atlassian Stash server and will report the test results as a comment.
This plugin was inspired by the GitHub & BitBucket pull request builder plugins.

- Official [Jenkins Plugin Page](https://wiki.jenkins-ci.org/display/JENKINS/Stash+pullrequest+builder+plugin)
- See this [blogpost](http://blog.nemccarthy.me/?p=387) for more details


## Prerequisites

- Jenkins 1.532 or higher.
- [Git Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin)

## Parameter variables

The plugin makes available to the job the following parameter variables:
- `${pullRequestId}`
- `${pullRequestTitle}`
- `${sourceBranch}`
- `${targetBranch}`
- `${sourceRepositoryOwner}`
- `${sourceRepositoryName}`
- `${destinationRepositoryOwner}`
- `${destinationRepositoryName}`
- `${sourceCommitHash}`
- `${destinationCommitHash}`

## Creating a Job

**Source Code Management**

Select *Git* then configure:

- **Repository URL**: `git@example.com:/${destinationRepositoryOwner}/${destinationRepositoryName}.git`
- **Advance -> Refspec**: `+refs/pull-requests/*:refs/remotes/origin/pr/*`
- **Branch Specifier**: `origin/pr/${pullRequestId}/from`

**Build Triggers**

Select *Stash Pull Request Builder* then configure:

- **Cron**: must be specified. eg: every 2 minute `H/2 * * * *`
- **Stash Host**: the *http* or *https* URL of the Stash host (NOT *ssh*). eg: *https://example.com*
- **Stash Credentials**: Select or Add the login username/password for the Stash Host
- **Project**: abbreviated project code. eg: *PRJ* or *~user*
- **RepositoryName**: eg: *Repo*

**Advanced options**
- Ignore ssl certificates:
- Build PR targetting only these branches: common separated list of branch names (or regexes). Blank for all.
- Rebuild if destination branch changes:
- Build only if Stash reports no conflicts: this should be set if using the merge branch to avoid issues with out of data merge branch in stash
- Build only if PR is mergeable (note this will stop the PR being built if you have required approvers limit set >0 and the PR hasn't been approved)
- Cancel outdated jobs
- CI Skip Phrases: default: "NO TEST"
- Only build when asked (with test phrase):
- CI Build Phrases: default: "test this please"
- Target branches: a comma separated list of branches (e.g. brancha,branchb)

## Building the merge of Source Branch into Target Branch

You may want Jenkins to build the merged PR (that is the merge of `sourceBranch` into `targetBranch`) to catch any issues resulting from this. To do this change the Branch Specifier from `origin/pr/${pullRequestId}/from` to `origin/pr/${pullRequestId}/merge`

If you are building the merged PR you probably want Jenkins to do a new build when the target branch changes. There is an advanced option in the build trigger, "Rebuild if destination branch changes?" which enables this.

You probably also only want to build if the PR was mergeable and always without conflicts. There are advanced options in the build trigger for both of these. 

**NOTE: *Always enable `Build only if Stash reports no conflicts` if using the merge RefSpec!*** This will make sure the lazy merge on stash has happened before the build is triggered.

#### Merging Locally
If you dont want to use the lazy merged Stash PR RefSpec (described above) the other option is to do the merge locally as part of the build using the Jenkins git plugin (these only work for branches within the same repo);

1. Select Git SCM
2. Add Repository URL as bellow
   `git@myStashHost.com:${projectCode}/${repositoryName}.git`
3. In Branch Specifier, type as bellow
   `*/${sourceBranch}`
4. In the "Source Code Management" > "Git" > "Additional Behaviors" section, click "Add" > "Merge Before Building"
5. In "Name of Repository" put "origin" (or, if not using default name, use your remote repository's name. Note: unlike in the main part of the Git Repository config, you cannot leave this item blank for "default".)
6. In "Branch to merge to" put `${targetBranch}`
  - Note that as long as you don't push these changes to your remote repository, the merge only happens in your local repository.

Alternatively if you want to use Stash's `origin/pr/${pullRequestId}/from` branch specifier and merge locally to avoid the race condition without checking stash if the PR is conflicted using the `merge` branch spec then;

1. Use the `origin/pr/${pullRequestId}/from` branch specifier
2. In the "Source Code Management" > "Git" > "Additional Behaviors" section, click "Add" > "Merge Before Building"
3. Set the "Branch to merge to" to `${targetBranch}`
4. Alternatively to the above 3 steps, just run a `git merge $destinationCommitHash`

If you have downstream jobs that are not triggered by this plugin you can simply add a if condition on this command to check if the parameters are available;

```
if [ ! -z "$destinationCommitHash" ]; then
    git merge $destinationCommitHash
fi
```

## Notify Stash of build result
If you are using the [StashNotifier plugin](https://wiki.jenkins-ci.org/display/JENKINS/StashNotifier+Plugin) and have enabled the 'Notify Stash Instance' Post-build Action while building the merged PR, you need to set `${sourceCommitHash}` as Commit SHA-1 to record the build result against the source commit.

## Rerun test builds

If you want to rerun pull request test, write *"test this please"* comment to your pull request.

##Adding additional parameters to a build

If you want to add additional parameters to the triggered build, add comments using the pattern `p:<parameter_name>=<value>`, one at each line, prefixed with `p:`. If the same parameter name appears multiple times the latest comment with that parameter will decide the value.

**Example:**

    test this please
    p:country=USA
    p:env=dev1


## Post Build Comment

It is possible to add a post build action that gives the option to post additional information to Stash when a build has been either successful or failed.
These comments can contain environment variables that will be translated when posted to Stash.

This feature can be used to post for instance a url to the deployed application or code coverage at a successful build and why the build failed like what tests that did not pass.


## License

- BSD License
