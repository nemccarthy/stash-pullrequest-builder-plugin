Stash Pull Request Builder Plugin [![Build Status](https://travis-ci.org/nemccarthy/stash-pullrequest-builder-plugin.svg?branch=master)](https://travis-ci.org/nemccarthy/stash-pullrequest-builder-plugin)
================================

This Jenkins plugin builds pull requests from a Atlassian Stash server and will report the test results as a comment.
This plugin was inspired by the GitHub & BitBucket pull request builder plugins.

- See this [blogpost](http://blog.nemccarthy.me/?p=387) for more details; http://blog.nemccarthy.me/?p=387 


##Prerequisites

- Jenkins 1.532 or higher.
- Git Plugin - https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin


##Creating a Job

- Create a new job
- Select Git SCM
- Add Repository URL as bellow
  - git@myStashHost.com:${projectCode}/${repositoryName}.git
- In Branch Specifier, type as bellow
  - */${sourceBranch}
- Under Build Triggers, check Stash Pull Request Builder
- In Cron, enter crontab for this job.
  - e.g. every minute: * * * * *
- In Stash BasicAuth Username - Stash username like jenkins-buildbot
- In Stash BasicAuth Password - Jenkins Build Bot password
- Supply project code (this is the abbreviated project code, e.g. PRJ)
- Supply Repository Name (e.g. myRepo)
- Save to preserve your changes

##Merge the Pull Request's Source Branch into the Target Branch Before Building

You may want Jenkins to attempt to merge your PR before doing the build -- this way it will find conflicts for you automatically.

- Follow the steps above in "Creating a Job"
- In the "Source Code Management" > "Git" > "Additional Behaviors" section, click "Add" > "Merge Before Building"
- In "Name of Repository" put "origin" (or, if not using default name, use your remote repository's name. Note: unlike in the main part of the Git Repository config, you cannot leave this item blank for "default".)
- In "Branch to merge to" put "${targetBranch}" 
- Note that as long as you don't push these changes to your remote repository, the merge only happens in your local repository.


If you are merging into your target branch, you might want Jenkins to do a new build of the Pull Request when the target branch changes.
- There is a checkbox that says, "Rebuild if destination branch changes?" which enables this check.


##Rerun test builds


If you want to rerun pull request test, write *“test this please”* comment to your pull request.



##Copyright

Copyright © 2015 Nathan McCarthy.


##License

- BSD License
