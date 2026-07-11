; 标签名 (如 <div> 中的 div)
(tag_name) @tag

; 错误的结束标签名
(erroneous_end_tag_name) @tag.error

; doctype 声明
(doctype) @constant

; 属性名和属性值
(attribute_name) @attribute
(attribute_value) @string
(quoted_attribute_value) @string

; 注释
(comment) @comment

; 文本内容
(text) @text

; 实体引用 (如 &nbsp; &amp;)
(entity) @keyword

; 标签括号
[
  "<"
  ">"
  "</"
  "/>"
  "<!"
] @punctuation.bracket

; 属性赋值符号
"=" @operator
