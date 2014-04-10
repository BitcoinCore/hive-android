/* Substrate layer to allow callback functions in calls to the Hive API */
var bitcoin = (function(Zepto) {
    var MAX_CALLBACK_ID = 2147483647;
    var nextCallbackId = 0;
    var callbacks = {};

    var nextId = function() {
        var id = nextCallbackId;

        nextCallbackId += 1;
        if (nextCallbackId == MAX_CALLBACK_ID) {
            nextCallbackId = 0;
        }

        return id;
    };

    var withCallback = function(func, callback, params) {
        var callbackId = nextId();
        var args = Array.prototype.slice.call(arguments, 2);

        args.unshift(callbackId);
        callbacks[callbackId] = callback;
        func.apply(__bitcoin, args);
    };

    return {
        BTC_IN_SATOSHI: 100000000,
        MBTC_IN_SATOSHI: 100000,
        UBTC_IN_SATOSHI: 100,

        __callbackFromAndroid: function(callbackId, params) {
            var callback = callbacks[callbackId];

            if (callback && typeof(callback) === 'function') {
                args = Array.prototype.slice.call(arguments, 1);
                callback.apply(null, args);
            }

            delete callbacks[callbackId];
        },

        getUserInfo: function(callback) {
            withCallback(__bitcoin.getUserInfo, callback);
        },

        getSystemInfo: function(callback) {
            withCallback(__bitcoin.getSystemInfo, callback);
        },

        userStringForSatoshi: function(amount) {
            return __bitcoin.userStringForSatoshi(amount);
        },

        satoshiFromUserString: function(amountStr) {
            return __bitcoin.satoshiFromUserString(amountStr);
        },

        sendMoney: function(address, amount, callback) {
            if (amount)
                withCallback(__bitcoin.sendMoney1, callback, address, amount);
            else
                withCallback(__bitcoin.sendMoney2, callback, address);
        },

        makeRequest: function(url, options) {
            options['url'] = url;
            Zepto.ajax(options);
        },

        getApplication: function(appId, callback) {
            withCallback(__bitcoin.getApplication, callback, appId);
        }
    }
}(Zepto));
