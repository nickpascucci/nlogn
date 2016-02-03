# Introduction to nlogn

nlogn is designed to run as a clojure web server with semi-dynamic
content. blog posts are read from markdown source files on the
filesystem and converted to html in response to user
requests. updating the blog is a matter of adding a new markdown
file.

# TODO

 - Design config file format for specifying the post paths,
   publication status, etc.
 - Convert posts from markdown to data structures and figure out how
   to render those to HTML with reusable templating
