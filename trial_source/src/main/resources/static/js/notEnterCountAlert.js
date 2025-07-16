document.addEventListener('DOMContentLoaded', function () {
    const hasNotEnterCount = document.getElementById('hasNotEnterCount').value;
    if (hasNotEnterCount) {
        alert("過去の勤怠に未入力があります。");
    }
});