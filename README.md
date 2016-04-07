Stash Pull Request Builder Plugin
================================

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
- Build only if Stash reports no conflicts:
- Build only if PR is mergeable:
- CI Skip Phrases: default: "NO TEST"
- Only build when asked (with test phrase):
- CI Build Phrases: default: "test this please"
- Target branches: a comma separated list of branches (e.g. brancha,branchb)

## Building the merge of Source Branch into Target Branch

You may want Jenkins to build the merged PR (that is the merge of `sourceBranch` into `targetBranch`) to catch any issues resulting from this. To do this change the Branch Specifier from `origin/pr/${pullRequestId}/from` to `origin/pr/${pullRequestId}/merge`

If you are building the merged PR you probably want Jenkins to do a new build when the target branch changes. There is an advanced option in the build trigger, "Rebuild if destination branch changes?" which enables this.

You probably also only want to build if the PR was mergeable, and possibly also without conflicts. There are advanced options in the build trigger for both of these.

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

## Copyright

Copyright © 2015 Nathan McCarthy.


## License

- BSD License
