<script setup>
</script>

<template>
  <div class="app-container">
    <router-view />
  </div>
</template>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  background-image: url('/back.png');
  background-size: cover;
  background-position: center;
  min-height: 100vh;
  position: relative;
}

body::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: url('/back.png');
  background-size: cover;
  background-position: center;
  filter: blur(10px);
  opacity: 0.6;
  z-index: -1;
}

.app-container {
  position: relative;
  width: min(900px, calc(100vw - 32px));
  height: min(760px, calc(100dvh - 48px));
  min-height: 320px;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12);
  z-index: 1;
  overflow: hidden;
}

.app-container router-view {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}
</style>
