Stash Pull Request Builder Plugin
================================

[![Join the chat at https://gitter.im/nemccarthy/stash-pullrequest-builder-plugin](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/nemccarthy/stash-pullrequest-builder-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

This Jenkins plugin builds pull requests from a Atlassian Stash server and will report the test results as a comment.
This plugin was inspired by the GitHub & BitBucket pull request builder plugins.

- Official [Jenkins Plugin Page](https://wiki.jenkins-ci.org/display/JENKINS/Stash+pullrequest+builder+plugin); https://wiki.jenkins-ci.org/display/JENKINS/Stash+pullrequest+builder+plugin
- See this [blogpost](http://blog.nemccarthy.me/?p=387) for more details; http://blog.nemccarthy.me/?p=387 


##Prerequisites

- Jenkins 1.532 or higher.
- Git Plugin - https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin


##Creating a Job

- Create a new job
- Select Git SCM
- Add Repository URL
	- Choose credentials (will be used by trigger)
	- Set refspec to: +refs/pull-requests/*:refs/remotes/origin/pr/*
- In Branch Specifier, type as bellow
  - ${pullRequest}
- Under Build Triggers, check Stash Pull Request Builder
- In Cron, enter crontab for this job.
  - e.g. every minute: * * * * *
- Save to preserve your changes

##SSH Stash url in Git SCM

If you use a SSH Stash url in the Git SCM you will have to set host, username and password in the advanced section 
of the plugin configuration. Reason is that the plugin calls the Stash REST API which is HTTP by definition.

##Merge the Pull Request's Source Branch into the Target Branch Before Building

You may want Jenkins to attempt to merge your PR before doing the build -- this way it will find conflicts for you automatically.

- Follow the steps above in "Creating a Job"
- In the "Source Code Management" > "Git" > "Additional Behaviors" section, click "Add" > "Merge Before Building"
- In "Name of Repository" put "origin" (or, if not using default name, use your remote repository's name. Note: unlike in the main part of the Git Repository config, you cannot leave this item blank for "default".)
- In "Branch to merge to" put the target branch you want to merge to 
- Note that as long as you don't push these changes to your remote repository, the merge only happens in your local repository.

By default it will only accept pull requests that match the target branch specified as target branch in the 'Merge before build' extension. You can choose to disable this or choose an custom filter to limit the number of pull requests being verified. Both options are available in the advanced tab.

If you are merging into your target branch, you might want Jenkins to do a new build of the Pull Request when the target branch changes.
- There is a checkbox that says, "Rebuild if destination branch changes?" which enables this check.

##Notify Stash instance

If you have enabled the 'Notify Stash Instance' Post-build Action and also enabled the 'Merge before build' extension you need to add '${pullRequestCommit}' as Commit SHA-1. Otherwise you'll notify Stash with the commit hash resulting from the merge which isn't known to Stash (since it's merged locally).

##Rerun test builds

If you want to rerun pull request test, write *“test this please”* comment to your pull request.


## Post Build Comment

It is possible to add a post build action that gives the option to post additional information to Stash when a build has been either successful or failed.
These comments can contain environment variables that will be translated when posted to Stash.

This feature can be used to post for instance a url to the deployed application or code coverage at a successful build and why the build failed like what tests that did not pass.

##Copyright

Copyright © 2015 Nathan McCarthy.


##License

- BSD License
