var exec = require('cordova/exec');
module.exports = {
	pay: function (message, win, fail) {
        exec(win, fail, 'Wxpay', 'pay', [message]);
	}
};