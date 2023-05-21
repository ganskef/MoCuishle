function save_options() {
  var portNumber = document.getElementById("portNumber").checked;
  chrome.storage.local.set(
    {
      portNumber: portNumber,
    },
    function () {
      var status = document.getElementById("status");
      status.textContent = "Options saved.";
      setTimeout(function () {
        status.textContent = "";
      }, 750);
    }
  );
}

function restore_options() {
  chrome.storage.local.get(
    {
      portNumber: 9090,
    },
    function (items) {
      document.getElementById("portNumber").checked = items.portNumber;
    }
  );
}

document.addEventListener("DOMContentLoaded", restore_options);
document.getElementById("save").addEventListener("click", save_options);
