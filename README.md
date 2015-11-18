Stash Pull Request Builder Plugin
================================

[![Join the chat at https://gitter.im/nemccarthy/stash-pullrequest-builder-plugin](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/nemccarthy/stash-pullrequest-builder-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This Jenkins plugin builds pull requests from a Atlassian Stash server and will report the test results as a comment.
This plugin was inspired by the GitHub & BitBucket pull request builder plugins.

- Official [Jenkins Plugin Page](https://wiki.jenkins-ci.org/display/JENKINS/Stash+pullrequest+builder+plugin)
- See this [blogpost](http://blog.nemccarthy.me/?p=387) for more details


##Prerequisites

- Jenkins 1.532 or higher.
- [Git Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin)

##Parameter variables

The plugin makes available to the job the following parameter variables:
- `${sourceBranch}`
- `${targetBranch}`
- `${projectCode}`
- `${repositoryName}`
- `${pullRequestId}`
- `${destinationRepositoryOwner}`
- `${destinationReposotryName}`
- `${pullRequestTitle}`
- `${sourceCommitHash}`

##Creating a Job

**Source Code Management**

Select *Git* then configure:

- **Repository URL**: `git@example.com:${projectCode}/${repositoryName}.git`
- **Branch Specifier**: `*/${sourceBranch}`

**Build Triggers**

Select *Stash Pull Request Builder* then configure:

- **Cron**: must be specified. eg: every 2 minute `H/2 * * * *`
- **Stash Host**: the *http* or *https* URL of the Stash host (NOT *ssh*). eg: *https://example.com*
- **Stash BasicAuth Username**: eg: *jenkins-buildbot*
- **Stash BasicAuth Password**: password for the given Username
- **Project**: abbreviated project code. eg: *PRJ* or *~user*
- **RepositoryName**: eg: *Repo*
- **CI Skip Phrases**: optional. eg: *"Don't test"*

**Advanced options**
- Ignore ssl certificates:
- Rebuild if destination branch changes?:
- Build only if Stash reports no conflicts?:
- Build only if PR is mergeable?:
- Only build when asked (with test phrase)?:
- CI Build Phrases:

##Merge the Pull Request's Source Branch into the Target Branch Before Building

You may want Jenkins to attempt to merge your PR before doing the build -- this way it will find conflicts for you automatically.

- Follow the steps above in "Creating a Job"
- In the "Source Code Management" > "Git" > "Additional Behaviors" section, click "Add" > "Merge Before Building"
- In "Name of Repository" put `origin` (or, if not using default name, use your remote repository's name.
  - Note: unlike in the main part of the Git Repository config, you cannot leave this item blank for "default".)
- In "Branch to merge to" put `${targetBranch}"`

Note that as long as you don't push these changes to your remote repository, the merge only happens in your local repository.

If you are merging into your target branch, you might want Jenkins to do a new build of the Pull Request when the target branch changes. There is a advanced option in the build trigger, "Rebuild if destination branch changes?" which enables this.

##Notify Stash Instance (StashNotifier plugin)

If you are using the StashNotifier plugin and have enabled the 'Notify Stash Instance' Post-build Action while also enabled 'Merge before build', you need to set `${sourceCommitHash}` as Commit SHA-1.  This will record the build result against the source commit.

##Rerun test builds

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

##Copyright

Copyright © 2015 Nathan McCarthy.


##License

- BSD License
