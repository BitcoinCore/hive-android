/* Substrate layer to allow callback functions in calls to the Hive API */
var bitcoin = (function() {
    var API_VERSION_MAJOR = 0;
    var API_VERSION_MINOR = 2;
    var MAX_CALLBACK_ID = 2147483647;
    var CALLBACK_TYPE_SIMPLE = 0;
    var CALLBACK_TYPE_MAKE_REQUEST = 1;
    var nextCallbackId = 0;
    var callbacks = {};
    var listeners = [];

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
        callbacks[callbackId] = { 'type': CALLBACK_TYPE_SIMPLE
                                , 'function': callback
                                };
        func.apply(__bitcoin, args);
    };

    var withMakeRequestCallback = function(func, options, params) {
        var callbackId = nextId();
        var args = Array.prototype.slice.call(arguments, 2);

        args.unshift(callbackId)
        callbacks[callbackId] = { 'type': CALLBACK_TYPE_MAKE_REQUEST
                                , 'options': options
                                };
        func.apply(__bitcoin, args);
    };

    return {
        BTC_IN_SATOSHI: 100000000,
        MBTC_IN_SATOSHI: 100000,
        UBTC_IN_SATOSHI: 100,
        TX_TYPE_INCOMING: 'incoming',
        TX_TYPE_OUTGOING: 'outgoing',
        apiVersionMajor: API_VERSION_MAJOR,
        apiVersionMinor: API_VERSION_MINOR,

        __callbackFromAndroid: function(callbackId, params) {
            var callback = callbacks[callbackId];
            delete callbacks[callbackId];

            if (!callback)
                return;

            type = callback['type'];
            switch (type) {
                case CALLBACK_TYPE_SIMPLE:
                    func = callback['function'];
                    if (!func || typeof(func) !== 'function')
                        return

                    args = Array.prototype.slice.call(arguments, 1);
                    func.apply(null, args);

                    break;
                case CALLBACK_TYPE_MAKE_REQUEST:
                    options = callback['options'];
                    if (!options)
                        return;

                    success = options['success'];
                    error = options['error'];
                    complete = options['complete'];

                    dataType = options['dataType'];
                    forceJSON = dataType && dataType.toLowerCase() === 'json';

                    wasSuccessful = arguments[1];
                    if (wasSuccessful) {
                        contentType = arguments[2];
                        args = Array.prototype.slice.call(arguments, 3);

                        args[0] = atob(args[0]);
                        if (contentType.toLowerCase().indexOf("json") >= 0
                                || forceJSON) {
                            args[0] = JSON.parse(args[0]);
                        }

                        if (success && typeof(success) === 'function')
                            success.apply(null, args);

                        if (complete && typeof(complete) === 'function')
                            complete.apply(null, args);
                    } else {
                        args = Array.prototype.slice.call(arguments, 2);
                        args[0] = atob(args[0]);

                        if (error && typeof(error) === 'function')
                            error.apply(null, args);

                        if (complete && typeof(complete) === 'function')
                            complete.apply(null, args);
                    }
                    break;
            }
        },

        __exchangeRateUpdateFromAndroid: function(rates) {
            for (var i = 0; i < listeners.length; i++) {
                listener = listeners[i];

                if (typeof(listener) !== 'function')
                    continue;

                for (rate in rates) {
                    if (!rates.hasOwnProperty(rate))
                        continue;

                    listener(rate, rates[rate]);
                }
            }
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

        getApplication: function(appId, callback) {
            withCallback(__bitcoin.getApplication, callback, appId);
        },

        installApp: function(url, callback) {
            withCallback(__bitcoin.installApp, callback, url);
        },

        getTransaction: function(txid, callback) {
            withCallback(__bitcoin.getTransaction, callback, txid);
        },

        addExchangeRateListener: function(callback) {
            listeners.push(callback);
            __bitcoin.subscribeToExchangeRateUpdates();
        },

        removeExchangeRateListener: function(callback) {
            var idx = listeners.indexOf(callback);
            if (idx >= 0)
                listeners.splice(idx, 1);

            if (listeners.length == 0)
                __bitcoin.unsubscribeFromExchangeRateUpdates();
        },

        updateExchangeRate: function(currency) {
            __bitcoin.updateExchangeRate(currency);
        },

        makeRequest: function(url, options) {
            type = options['type'];
            if (!type)
                type = "GET";

            data = options['data'];
            if (!data) {
                data = "";
            } else if (typeof(data) === 'object') {
                var params = [];
                for (key in data) {
                    params.push(encodeURIComponent(key) + '='
                            + encodeURIComponent(data[key]));
                }
                data = params.join('&');
            }

            withMakeRequestCallback(__bitcoin.makeRequest,
                    options, url, type, data);
        }
    }
}());
