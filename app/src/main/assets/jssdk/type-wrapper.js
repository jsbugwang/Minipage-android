(function() {
  function getType(obj) {
    return Object.prototype.toString.call(obj).match(/object (\w+)/i)[1]
  }
  function isType(type) {
    return function(obj) {
      return getType(obj) === type
    }
  }
  let isArray = isType('Array')
  let isObject = isType('Object') // 注意：这里的定义，数组、null 都不是 Object，null 可以用 isNull 来判断

  // param 不要传 null
  function isPrimitive(param) {
    // 如果是数组，那么每一个元素只能是 primitive 类型
    if (isArray(param)) {
      if (param.length === 0) return true

      // 非空数组
      let type = getType(param[0])
      
      // 不是 primitive
      if (type === 'Object' || type === 'Array') return false

      return param.every(item => getType(item) === type)
    }

    return !isObject(param)
  }

  function injectJsSDKWithJsType() {
    const JSBridge = {}
    // 注入后 JS 获取到的 StringJSBridge.testString.toString() 都是 function() { [native code] } ，参数签名已丢失
    Object.keys(StringJSBridge).forEach(apiName => {
      // Native的方法名用 _ 作为前缀标记的，是返回值为 数据结构 的
      let isReturnDataStructure = apiName.startsWith('_')
      let newApiName = apiName.replace(/^_/, '')
      JSBridge[newApiName] = function(...args) {
        // 自动识别实参类型，如果是 `非Primitive` 的，自动转换成字符串传给 Native
        const newArgs = args.map(arg => isPrimitive(arg) ? arg : JSON.stringify(arg))
        let nativeReturnValue = StringJSBridge[apiName](...newArgs)
        return isReturnDataStructure ? JSON.parse(nativeReturnValue) : nativeReturnValue
      }
    })

    // 把 JSBridge 暴露到 window 全局对象下
    window.JSBridge = JSBridge

    console.log('→→→→→→→→→→→→→→→→→→→成功注入SDK: ', JSBridge)
  }

  injectJsSDKWithJsType()
})()

